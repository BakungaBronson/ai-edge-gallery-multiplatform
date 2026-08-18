/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import CLiteRTLM
import Foundation
import shared

/// Swift bridge to the LiteRT-LM v0.16 C-API (`CLiteRTLM.xcframework`), the iOS counterpart of
/// Android's `crane_llm_jni.c` + `CraneLlm.kt`.
///
/// Android needs a C shim because the JVM cannot call C directly; Swift can, so this file *is*
/// the shim — same layer, same responsibilities, one language over. It applies the Crane
/// decoding guards per-send (`repetition_penalty` + `no_repeat_ngram`) and the system prompt
/// template-safely at conversation creation, which is what the MediaPipe delegate it replaces
/// could not do.
///
/// Mirrors `crane_llm_jni.c` configuration exactly:
///   - CPU backend, benchmark enabled
///   - system message applied via conversation config (template-safe)
///   - per-turn optional args: max_output_tokens + repetition_penalty + no_repeat_ngram
///
/// The xcframework is not committed: `:shared`'s `downloadCLiteRtLmXcframework` Gradle task
/// fetches and sha256-verifies the pinned v0.16.0 release asset at build time, mirroring
/// `:app`'s `downloadLiteRtLmCApi`.
final class CraneLlmDelegate: IosLlmDelegate {

    /// Guarded by `engineLock`. Read from the conversation path, written from `workQueue`.
    private var engine: OpaquePointer?
    private let engineLock = NSLock()

    /// Serialises engine create/destroy. Model loading takes seconds to minutes for a
    /// multi-GB bundle, so it must not run on whatever thread Kotlin called us from — and
    /// ordering create-after-destroy matters when a screen is re-entered quickly.
    private let workQueue = DispatchQueue(label: "com.google.ai.edge.gallery.crane-llm")

    func initialize(
        modelPath: String,
        backend: String,
        maxTokens: Int32,
        cacheDir: String?,
        onDone: @escaping (String) -> Void
    ) {
        workQueue.async { [weak self] in
            guard let self else { return }

            self.replaceEngine(with: nil)

            // The C-API path is CPU-only, same as the Android Crane engine — a GPU request is
            // honoured as CPU rather than failing, and said so out loud.
            if backend != "cpu" {
                NSLog("[CraneLlm] C-API path is CPU-only; ignoring requested backend '\(backend)'")
            }

            guard FileManager.default.fileExists(atPath: modelPath) else {
                onDone("Model file not found: \(modelPath)")
                return
            }

            guard let settings = litert_lm_engine_settings_create(modelPath, "cpu", nil, nil) else {
                onDone("Failed to create engine settings for \(modelPath)")
                return
            }
            litert_lm_engine_settings_enable_benchmark(settings)

            let created = litert_lm_engine_create(settings)
            litert_lm_engine_settings_delete(settings)

            guard let created else {
                onDone("Failed to load model: \(modelPath)")
                return
            }
            self.replaceEngine(with: created)
            NSLog("[CraneLlm] engine created for \(modelPath)")
            onDone("")
        }
    }

    func createConversation(
        topK: Int32,
        topP: Double,
        temperature: Double,
        systemInstruction: String?
    ) -> IosLlmConversationDelegate {
        // topK/topP/temperature are accepted but not applied, exactly as on Android's C-API
        // path (crane_llm_jni.c sets no session config either). Keeping the two platforms
        // identical here is what makes the guards the only variable in the ON/OFF comparison;
        // the engine's own sampler defaults apply on both.
        guard let engine = currentEngine() else {
            return CraneLlmConversation(conversation: nil, error: "Engine not initialized")
        }

        guard let config = litert_lm_conversation_config_create() else {
            return CraneLlmConversation(conversation: nil, error: "Failed to create conversation config")
        }
        defer { litert_lm_conversation_config_delete(config) }

        // The system prompt goes in as a full JSON message so the engine renders it through the
        // model's chat template instead of concatenating it into the user turn. Unlike the
        // streaming path below, this call consumes the string before returning, so handing it a
        // Swift String's temporary buffer is safe.
        if let systemInstruction,
           !systemInstruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           let systemJson = Self.jsonMessage(role: "system", content: systemInstruction) {
            litert_lm_conversation_config_set_system_message(config, systemJson)
        }

        guard let conversation = litert_lm_conversation_create(engine, config) else {
            return CraneLlmConversation(conversation: nil, error: "Failed to create conversation")
        }
        return CraneLlmConversation(conversation: conversation, error: nil)
    }

    func close() {
        // Deliberately async, not sync: the model manager calls close() from inside
        // initialize()'s completion handler when a screen was re-entered mid-load
        // (Model.cleanUpAfterInit), and that handler already runs on workQueue — a sync hop
        // would deadlock on itself. The queue is serial, so a close() followed by an
        // initialize() still tears down before it rebuilds.
        workQueue.async { [weak self] in
            self?.replaceEngine(with: nil)
        }
    }

    private func currentEngine() -> OpaquePointer? {
        engineLock.lock()
        defer { engineLock.unlock() }
        return engine
    }

    /// Swaps the engine pointer under the lock and destroys whatever it replaced. Only ever
    /// called from `workQueue`, so two creates can't race each other.
    private func replaceEngine(with new: OpaquePointer?) {
        engineLock.lock()
        let old = engine
        engine = new
        engineLock.unlock()
        if let old { litert_lm_engine_delete(old) }
    }

    /// Builds `{"role":"...","content":"..."}` with correct escaping.
    static func jsonMessage(role: String, content: String) -> String? {
        let payload: [String: String] = ["role": role, "content": content]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8) else {
            return nil
        }
        return json
    }
}

/// Per-send state handed to the C callback as `callback_data`.
///
/// Retained across the `send_message_stream` call via `Unmanaged` and released exactly once,
/// when the stream terminates (final chunk, error chunk, or a failure to start).
///
/// It also *owns* the message buffer and the optional args. `send_message_stream` is
/// non-blocking and the C API does not copy either one — `crane_llm_jni.c` gets away with stack
/// heap locals only because it blocks on a condvar until the final chunk before freeing them.
/// This bridge returns immediately, so the lifetime has to be tied to the stream instead: both
/// are freed in `deinit`, which runs when the callback releases the context.
private final class StreamContext {
    let onToken: (String) -> Void
    let onDone: () -> Void
    let onError: (String) -> Void

    /// NUL-terminated copy of the message JSON, valid for the whole stream.
    let messageJson: UnsafeMutablePointer<CChar>?
    let optionalArgs: OpaquePointer?

    /// Serialises the callback thread against the "failed to start" path so the context is
    /// never released twice.
    private var finished = false
    private let lock = NSLock()

    init(
        messageJson: UnsafeMutablePointer<CChar>?,
        optionalArgs: OpaquePointer?,
        onToken: @escaping (String) -> Void,
        onDone: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        self.messageJson = messageJson
        self.optionalArgs = optionalArgs
        self.onToken = onToken
        self.onDone = onDone
        self.onError = onError
    }

    deinit {
        if let messageJson { free(messageJson) }
        if let optionalArgs { litert_lm_conversation_optional_args_delete(optionalArgs) }
    }

    /// Returns true exactly once, for whoever terminates the stream first.
    func claimFinish() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if finished { return false }
        finished = true
        return true
    }
}

/// C callback. Runs on a LiteRT-LM-owned background thread.
private let craneStreamCallback: LiteRtLmStreamCallback = { callbackData, chunk in
    guard let callbackData else { return }
    let ctx = Unmanaged<StreamContext>.fromOpaque(callbackData).takeUnretainedValue()

    let text = litert_lm_stream_chunk_get_text(chunk).map { String(cString: $0) }
    let error = litert_lm_stream_chunk_get_error(chunk).map { String(cString: $0) }
    let hasError = !(error ?? "").isEmpty
    // Treat an error chunk as terminal so a blocked caller always wakes up — same rule as
    // crane_llm_jni.c.
    let isFinal = litert_lm_stream_chunk_is_final(chunk) || hasError

    if let text, !text.isEmpty {
        let plain = CraneLlmConversation.extractChunkText(text)
        if !plain.isEmpty {
            ctx.onToken(plain)
        }
    }

    if isFinal, ctx.claimFinish() {
        if hasError {
            NSLog("[CraneLlm] stream chunk error: \(error ?? "")")
            ctx.onError(error ?? "Unknown inference error")
        } else {
            ctx.onDone()
        }
        Unmanaged<StreamContext>.fromOpaque(callbackData).release()
    }
}

/// Swift implementation of `IosLlmConversationDelegate` over a LiteRT-LM conversation handle.
final class CraneLlmConversation: IosLlmConversationDelegate {

    private var conversation: OpaquePointer?
    private let creationError: String?
    private let lock = NSLock()

    init(conversation: OpaquePointer?, error: String?) {
        self.conversation = conversation
        self.creationError = error
    }

    func sendMessageAsync(
        text: String,
        imageBytes: [KotlinByteArray],
        audioBytes: [KotlinByteArray],
        maxOutputTokens: Int32,
        repetitionPenalty: Float,
        noRepeatNgramSize: Int32,
        onToken: @escaping (String) -> Void,
        onDone: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        // Compose state lives on the main thread, but the C API calls back from its own thread.
        // Hop every callback to main; DispatchQueue.main.async is FIFO from a single producer,
        // so token order is preserved.
        let mainToken: (String) -> Void = { t in DispatchQueue.main.async { onToken(t) } }
        let mainDone: () -> Void = { DispatchQueue.main.async { onDone() } }
        let mainError: (String) -> Void = { e in DispatchQueue.main.async { onError(e) } }

        if let creationError {
            mainError(creationError)
            return
        }
        guard let conversation = currentConversation() else {
            mainError("Conversation is closed")
            return
        }
        warnAboutDroppedContent(imageCount: imageBytes.count, audioCount: audioBytes.count)

        guard let json = CraneLlmDelegate.jsonMessage(role: "user", content: text),
              let messageBuf = strdup(json) else {
            mainError("Failed to encode message")
            return
        }

        let ctx = StreamContext(
            messageJson: messageBuf,
            optionalArgs: Self.makeOptionalArgs(
                maxOutputTokens: maxOutputTokens,
                repetitionPenalty: repetitionPenalty,
                noRepeatNgramSize: noRepeatNgramSize
            ),
            onToken: mainToken,
            onDone: mainDone,
            onError: mainError
        )
        let ctxPtr = Unmanaged.passRetained(ctx).toOpaque()

        NSLog(
            "[CraneLlm] send with guards: maxTokens=\(maxOutputTokens) "
                + "rep=\(repetitionPenalty) ngram=\(noRepeatNgramSize)"
        )

        let rc = litert_lm_conversation_send_message_stream(
            conversation, ctx.messageJson, nil, ctx.optionalArgs, craneStreamCallback, ctxPtr
        )

        if rc != 0 {
            // Stream never started, so the callback will not fire — release here instead.
            if ctx.claimFinish() {
                mainError("inference failed to start (rc=\(rc))")
                Unmanaged<StreamContext>.fromOpaque(ctxPtr).release()
            }
        }
    }

    func sendMessage(
        text: String,
        imageBytes: [KotlinByteArray],
        audioBytes: [KotlinByteArray],
        maxOutputTokens: Int32,
        repetitionPenalty: Float,
        noRepeatNgramSize: Int32
    ) -> String {
        if let creationError { return creationError }
        guard let conversation = currentConversation() else { return "Conversation is closed" }
        warnAboutDroppedContent(imageCount: imageBytes.count, audioCount: audioBytes.count)

        guard let json = CraneLlmDelegate.jsonMessage(role: "user", content: text),
              let messageBuf = strdup(json) else {
            return "Failed to encode message"
        }

        // Synchronous variant: accumulate on the callback thread and block until terminal.
        // No main-queue hop here — that would deadlock if the caller is already on main.
        let semaphore = DispatchSemaphore(value: 0)
        let buffer = TextAccumulator()

        let ctx = StreamContext(
            messageJson: messageBuf,
            optionalArgs: Self.makeOptionalArgs(
                maxOutputTokens: maxOutputTokens,
                repetitionPenalty: repetitionPenalty,
                noRepeatNgramSize: noRepeatNgramSize
            ),
            onToken: { buffer.append($0) },
            onDone: { semaphore.signal() },
            onError: { buffer.setError($0); semaphore.signal() }
        )
        let ctxPtr = Unmanaged.passRetained(ctx).toOpaque()

        let rc = litert_lm_conversation_send_message_stream(
            conversation, ctx.messageJson, nil, ctx.optionalArgs, craneStreamCallback, ctxPtr
        )
        if rc != 0 {
            if ctx.claimFinish() {
                Unmanaged<StreamContext>.fromOpaque(ctxPtr).release()
            }
            return "inference failed to start (rc=\(rc))"
        }

        semaphore.wait()
        return buffer.error ?? buffer.text
    }

    func cancel() {
        guard let conversation = currentConversation() else { return }
        litert_lm_conversation_cancel_process(conversation)
    }

    func close() {
        lock.lock()
        let handle = conversation
        conversation = nil
        lock.unlock()
        if let handle { litert_lm_conversation_delete(handle) }
    }

    private func currentConversation() -> OpaquePointer? {
        lock.lock()
        defer { lock.unlock() }
        return conversation
    }

    /// Builds the per-send optional args. A repetition penalty of <= 1.0 and an n-gram size of
    /// <= 0 mean "guard off" and are simply not set — identical semantics to the Android path,
    /// which is what lets the settings-sheet knob turn the guards off.
    ///
    /// Ownership passes to the StreamContext, which deletes it when the stream ends.
    private static func makeOptionalArgs(
        maxOutputTokens: Int32,
        repetitionPenalty: Float,
        noRepeatNgramSize: Int32
    ) -> OpaquePointer? {
        guard let args = litert_lm_conversation_optional_args_create() else { return nil }

        if maxOutputTokens > 0 {
            litert_lm_conversation_optional_args_set_max_output_tokens(args, maxOutputTokens)
        }
        if repetitionPenalty > 1.0 {
            if let rep = litert_lm_repetition_penalty_config_create() {
                litert_lm_repetition_penalty_config_set_repetition_penalty(rep, repetitionPenalty)
                litert_lm_conversation_optional_args_set_repetition_penalty_config(args, rep)
                litert_lm_repetition_penalty_config_delete(rep)  // deep-copied on set
            }
        }
        if noRepeatNgramSize > 0 {
            if let ngram = litert_lm_no_repeat_ngram_config_create() {
                litert_lm_no_repeat_ngram_config_set_no_repeat_ngram_size(ngram, noRepeatNgramSize)
                litert_lm_conversation_optional_args_set_no_repeat_ngram_config(args, ngram)
                litert_lm_no_repeat_ngram_config_delete(ngram)  // deep-copied on set
            }
        }
        return args
    }

    private func warnAboutDroppedContent(imageCount: Int, audioCount: Int) {
        let dropped = imageCount + audioCount
        if dropped > 0 {
            NSLog("[CraneLlm] C-API path is text-only; ignoring \(dropped) non-text content item(s)")
        }
    }

    /// The C-API streams each chunk as a JSON message string like
    /// `{"role":"assistant","content":[{"type":"text","text":"..."}]}`. Extract the plain text;
    /// fall back to the raw string for non-JSON chunks. Port of `CraneLlm.extractChunkText`.
    static func extractChunkText(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("{"), let data = trimmed.data(using: .utf8) else { return raw }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return raw
        }

        if let parts = obj["content"] as? [[String: Any]] {
            return parts.compactMap { $0["text"] as? String }.joined()
        }
        if let content = obj["content"] as? String {
            return content
        }
        return raw
    }
}

/// Tiny thread-safe accumulator for the synchronous send path.
private final class TextAccumulator {
    private var buffer = ""
    private var errorMessage: String?
    private let lock = NSLock()

    func append(_ s: String) {
        lock.lock()
        buffer += s
        lock.unlock()
    }

    func setError(_ e: String) {
        lock.lock()
        errorMessage = e
        lock.unlock()
    }

    var text: String {
        lock.lock()
        defer { lock.unlock() }
        return buffer
    }

    var error: String? {
        lock.lock()
        defer { lock.unlock() }
        return errorMessage
    }
}
