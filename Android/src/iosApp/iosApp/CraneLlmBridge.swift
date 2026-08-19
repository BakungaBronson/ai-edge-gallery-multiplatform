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

/// How long teardown waits for a cancelled stream to deliver its terminal chunk before giving up
/// on it. `litert_lm_conversation_cancel_process` documents no contract about firing the callback,
/// so this cannot be an unbounded wait.
private let streamCancelTimeout: TimeInterval = 5

/// Upper bound on the blocking `sendMessage` path. Generation on CPU is slow, so this is generous,
/// but it must be finite: the caller may be on the main thread, where an unbounded wait is an
/// unrecoverable UI freeze.
private let syncSendTimeout: TimeInterval = 300

/// Swift bridge to the LiteRT-LM v0.16 C-API (`CLiteRTLM.xcframework`), the iOS counterpart of
/// Android's `crane_llm_jni.c` + `CraneLlm.kt`.
///
/// Android needs a C shim because the JVM cannot call C directly; Swift can, so this file *is*
/// the shim — same layer, same responsibilities, one language over. It applies the Crane
/// decoding guards per-send (`repetition_penalty` + `no_repeat_ngram`) and the system prompt
/// template-safely at conversation creation.
///
/// Mirrors `crane_llm_jni.c` configuration exactly:
///   - CPU backend, benchmark enabled
///   - system message applied via conversation config (template-safe)
///   - per-turn optional args: max_output_tokens + repetition_penalty + no_repeat_ngram
///
/// ## Lifetime rules, and why they are stricter than the JNI original
///
/// `conversation.h` documents no ownership contract for `send_message_stream`'s arguments, no
/// promise that `cancel_process` delivers a terminal chunk, and no promise that
/// `conversation_delete` drains an in-flight stream. Every rule below assumes the conservative
/// reading of each of those silences.
///
/// `crane_llm_jni.c` is immune to most of this by construction: it blocks on a condvar until the
/// final chunk, so its stack-allocated context and locals necessarily outlive the stream. This
/// bridge returns immediately, which converts that implicit safety into an explicit heap
/// lifetime — and every teardown path has to earn it back.
final class CraneLlmDelegate: IosLlmDelegate {

    /// Guarded by `engineLock`. Read from the conversation path, written from `workQueue`.
    private var engine: OpaquePointer?
    private let engineLock = NSLock()

    /// Conversations created from the current engine, held weakly. A conversation holds a handle
    /// that the engine owns, so every one of these must be closed *before* the engine is deleted
    /// or the next send dereferences a freed engine.
    private let liveConversations = NSHashTable<CraneLlmConversation>.weakObjects()
    private let registryLock = NSLock()

    /// Serialises engine create/destroy. Model loading takes seconds to minutes for a multi-GB
    /// bundle, so it must not run on whatever thread Kotlin called us from — and ordering
    /// create-after-destroy matters when a screen is re-entered quickly.
    private let workQueue = DispatchQueue(label: "com.google.ai.edge.gallery.crane-llm")

    deinit {
        // Without this, dropping the delegate without calling close() strands a 1–4 GB model in
        // memory for the process lifetime, which on iOS means a jetsam kill rather than a leak.
        teardownEngine(replacementEngine: nil)
    }

    func initialize(
        modelPath: String,
        backend: String,
        maxTokens: Int32,
        cacheDir: String?,
        onDone: @escaping (String) -> Void
    ) {
        // Kotlin publishes this into Compose-observed state, so report on main like the send
        // callbacks do rather than from workQueue.
        let report: (String) -> Void = { msg in DispatchQueue.main.async { onDone(msg) } }

        workQueue.async { [weak self] in
            guard let self else {
                // The delegate died mid-load. Resume the Kotlin side rather than leaving its
                // continuation hanging forever.
                report("LLM delegate was released before initialization completed")
                return
            }

            self.teardownEngine(replacementEngine: nil)

            // The C-API path is CPU-only, same as the Android Crane engine — a GPU request is
            // honoured as CPU rather than failing, and said so out loud.
            if backend != "cpu" {
                NSLog("[CraneLlm] C-API path is CPU-only; ignoring requested backend '\(backend)'")
            }

            guard FileManager.default.fileExists(atPath: modelPath) else {
                report("Model file not found: \(modelPath)")
                return
            }

            guard let settings = litert_lm_engine_settings_create(modelPath, "cpu", nil, nil) else {
                report("Failed to create engine settings for \(modelPath)")
                return
            }
            litert_lm_engine_settings_enable_benchmark(settings)

            let created = litert_lm_engine_create(settings)
            litert_lm_engine_settings_delete(settings)

            guard let created else {
                report("Failed to load model: \(modelPath)")
                return
            }
            self.engineLock.lock()
            self.engine = created
            self.engineLock.unlock()
            NSLog("[CraneLlm] engine created for \(modelPath)")
            report("")
        }
    }

    func createConversation(
        topK: Int32,
        topP: Double,
        temperature: Double,
        systemInstruction: String?
    ) -> IosLlmConversationDelegate {
        // topK/topP/temperature are accepted but not applied, exactly as on Android's C-API path
        // (crane_llm_jni.c sets no session config either). The engine's own sampler defaults apply
        // on both platforms; keeping them identical is what makes the guards the only variable in
        // the ON/OFF comparison. See the note in the PR about the settings sliders for these.
        guard let engine = currentEngine() else {
            return CraneLlmConversation(conversation: nil, error: "Engine not initialized")
        }

        guard let config = litert_lm_conversation_config_create() else {
            return CraneLlmConversation(conversation: nil, error: "Failed to create conversation config")
        }
        defer { litert_lm_conversation_config_delete(config) }

        // The system prompt goes in as a full JSON message so the engine renders it through the
        // model's chat template instead of concatenating it into the user turn. Unlike the
        // streaming path, this call consumes the string before returning, so a Swift String's
        // temporary buffer is safe here.
        if let systemInstruction,
           !systemInstruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           let systemJson = Self.jsonMessage(role: "system", content: systemInstruction) {
            litert_lm_conversation_config_set_system_message(config, systemJson)
        }

        guard let conversation = litert_lm_conversation_create(engine, config) else {
            return CraneLlmConversation(conversation: nil, error: "Failed to create conversation")
        }
        let wrapper = CraneLlmConversation(conversation: conversation, error: nil)
        registryLock.lock()
        liveConversations.add(wrapper)
        registryLock.unlock()
        return wrapper
    }

    func close() {
        // Deliberately async, not sync: the model manager calls close() from inside initialize()'s
        // completion handler when a screen was re-entered mid-load (Model.cleanUpAfterInit), and
        // that handler runs on workQueue — a sync hop would deadlock on itself. The queue is
        // serial, so close() followed by initialize() still tears down before it rebuilds.
        workQueue.async { [weak self] in
            self?.teardownEngine(replacementEngine: nil)
        }
    }

    private func currentEngine() -> OpaquePointer? {
        engineLock.lock()
        defer { engineLock.unlock() }
        return engine
    }

    /// Closes every conversation created from the current engine, *then* swaps and deletes it.
    ///
    /// Order matters: a `LiteRtLmConversation` is owned by the engine that created it, so deleting
    /// the engine first leaves live conversations holding dangling handles — reachable in exactly
    /// the re-entered-mid-load case close() describes, where the chat panel still holds a
    /// conversation while the engine is swapped underneath it.
    private func teardownEngine(replacementEngine new: OpaquePointer?) {
        registryLock.lock()
        let live = liveConversations.allObjects
        liveConversations.removeAllObjects()
        registryLock.unlock()

        // Outside registryLock: close() cancels and waits, and must not block conversation
        // creation on an unrelated thread for the duration.
        for conversation in live {
            conversation.close()
        }

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
/// Owns the message buffer and the optional args: `send_message_stream` is non-blocking and the
/// header documents no ownership contract, so the conservative reading is that the C side keeps
/// reading both for the life of the stream. Freeing them when the send call returns would be a
/// use-after-free.
///
/// The `Unmanaged` retain taken at send time is released by the *conversation*, never by the
/// callback — see `craneStreamCallback`.
private final class StreamContext {
    let onToken: (String) -> Void
    let onDone: () -> Void
    let onError: (String) -> Void

    /// NUL-terminated copy of the message JSON, valid for the whole stream.
    let messageJson: UnsafeMutablePointer<CChar>?
    let optionalArgs: OpaquePointer?

    /// Signalled once, when the stream reaches a terminal chunk. Teardown waits on this before
    /// deleting the conversation.
    let finishedSignal = DispatchSemaphore(value: 0)

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

    var hasFinished: Bool {
        lock.lock()
        defer { lock.unlock() }
        return finished
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
///
/// Deliberately never releases the context. The header does not say whether a chunk can arrive
/// after `is_final`, and this function dereferences `callback_data` before it can possibly know
/// whether the stream is over — so releasing here would leave a window where a late chunk touches
/// freed memory. Ownership stays with the conversation, which releases it during teardown once the
/// stream is known to be over.
private let craneStreamCallback: LiteRtLmStreamCallback = { callbackData, chunk in
    guard let callbackData else { return }
    let ctx = Unmanaged<StreamContext>.fromOpaque(callbackData).takeUnretainedValue()

    // A late chunk after terminal must not re-enter the callbacks.
    if ctx.hasFinished { return }

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
        ctx.finishedSignal.signal()
    }
}

/// Swift implementation of `IosLlmConversationDelegate` over a LiteRT-LM conversation handle.
final class CraneLlmConversation: IosLlmConversationDelegate {

    private var conversation: OpaquePointer?
    private let creationError: String?
    private let lock = NSLock()

    /// Retained stream contexts, owned here rather than by the callback. Released only once the
    /// stream is known to be over.
    private var streams: [Unmanaged<StreamContext>] = []

    init(conversation: OpaquePointer?, error: String?) {
        self.conversation = conversation
        self.creationError = error
    }

    deinit {
        // Mirrors the delegate's deinit: a dropped conversation must not strand its native handle.
        close()
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
        // Hop every callback to main; DispatchQueue.main.async is FIFO from a single producer, so
        // token order is preserved.
        let mainToken: (String) -> Void = { t in DispatchQueue.main.async { onToken(t) } }
        let mainDone: () -> Void = { DispatchQueue.main.async { onDone() } }
        let mainError: (String) -> Void = { e in DispatchQueue.main.async { onError(e) } }

        reapFinishedStreams()

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
        let retained = Unmanaged.passRetained(ctx)
        lock.lock()
        streams.append(retained)
        lock.unlock()

        NSLog(
            "[CraneLlm] send with guards: maxTokens=\(maxOutputTokens) "
                + "rep=\(repetitionPenalty) ngram=\(noRepeatNgramSize)"
        )

        let rc = litert_lm_conversation_send_message_stream(
            conversation, ctx.messageJson, nil, ctx.optionalArgs,
            craneStreamCallback, retained.toOpaque()
        )

        if rc != 0 {
            // The stream never started, so no callback will fire and this context is provably
            // unreferenced by the C side — safe to drop immediately.
            if ctx.claimFinish() {
                ctx.finishedSignal.signal()
                mainError("inference failed to start (rc=\(rc))")
                lock.lock()
                streams.removeAll { $0.toOpaque() == retained.toOpaque() }
                lock.unlock()
                retained.release()
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
        reapFinishedStreams()

        if let creationError { return creationError }
        guard let conversation = currentConversation() else { return "Conversation is closed" }
        warnAboutDroppedContent(imageCount: imageBytes.count, audioCount: audioBytes.count)

        guard let json = CraneLlmDelegate.jsonMessage(role: "user", content: text),
              let messageBuf = strdup(json) else {
            return "Failed to encode message"
        }

        // Synchronous variant: accumulate on the callback thread and block until terminal. No
        // main-queue hop here — that would deadlock if the caller is already on main.
        let buffer = TextAccumulator()
        let ctx = StreamContext(
            messageJson: messageBuf,
            optionalArgs: Self.makeOptionalArgs(
                maxOutputTokens: maxOutputTokens,
                repetitionPenalty: repetitionPenalty,
                noRepeatNgramSize: noRepeatNgramSize
            ),
            onToken: { buffer.append($0) },
            onDone: {},
            onError: { buffer.setError($0) }
        )
        let retained = Unmanaged.passRetained(ctx)
        lock.lock()
        streams.append(retained)
        lock.unlock()

        let rc = litert_lm_conversation_send_message_stream(
            conversation, ctx.messageJson, nil, ctx.optionalArgs,
            craneStreamCallback, retained.toOpaque()
        )
        if rc != 0 {
            if ctx.claimFinish() {
                ctx.finishedSignal.signal()
                lock.lock()
                streams.removeAll { $0.toOpaque() == retained.toOpaque() }
                lock.unlock()
                retained.release()
            }
            return "inference failed to start (rc=\(rc))"
        }

        // Bounded: this may be called from the main thread, where waiting forever on a stream that
        // never terminates is an unrecoverable freeze rather than a slow response.
        if ctx.finishedSignal.wait(timeout: .now() + syncSendTimeout) == .timedOut {
            litert_lm_conversation_cancel_process(conversation)
            return "inference timed out after \(Int(syncSendTimeout))s"
        }
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
        let pending = streams
        streams = []
        lock.unlock()

        guard let handle else { return }

        // Deleting a conversation with a stream still running is undefined by the header, so cancel
        // and give each stream a bounded chance to deliver its terminal chunk first. Without this,
        // navigating back mid-generation would delete the conversation underneath a live stream and
        // strand the Kotlin callbacks, which never resume.
        let unfinished = pending.filter { !$0.takeUnretainedValue().hasFinished }
        if !unfinished.isEmpty {
            // deinit calls close(), and deinit runs on whatever thread drops the last reference —
            // possibly main. So this wait can in principle stall the UI for up to
            // streamCancelTimeout per stream. Log it so that shows up as an identifiable hitch
            // rather than an unexplained freeze.
            NSLog("[CraneLlm] closing with \(unfinished.count) stream(s) still running; cancelling and waiting up to \(Int(streamCancelTimeout))s each")
            litert_lm_conversation_cancel_process(handle)
        }

        for stream in pending {
            let ctx = stream.takeUnretainedValue()
            if ctx.hasFinished {
                stream.release()
                continue
            }
            if ctx.finishedSignal.wait(timeout: .now() + streamCancelTimeout) == .timedOut {
                // The stream never acknowledged the cancel. The C side may still hold this
                // pointer, so intentionally leak the context (a few KB plus the message buffer)
                // rather than free memory it could still dereference. Bounded and rare; a
                // use-after-free would not be.
                NSLog("[CraneLlm] stream did not terminate within \(Int(streamCancelTimeout))s; leaking its context")
                continue
            }
            stream.release()
        }

        litert_lm_conversation_delete(handle)
    }

    private func currentConversation() -> OpaquePointer? {
        lock.lock()
        defer { lock.unlock() }
        return conversation
    }

    /// Releases contexts whose streams have terminated.
    ///
    /// Moving ownership off the callback (so a late chunk cannot touch freed memory) means nothing
    /// frees a context at the moment its stream ends any more. Without this sweep every successful
    /// send would retain its message buffer and optional args until the conversation closed —
    /// unbounded growth proportional to turn count, on a device already holding a multi-GB model.
    ///
    /// Safe because it releases only where `hasFinished` is already true, which is the same
    /// condition `close()` relies on, and it runs on a thread we control rather than inside the
    /// callback. That is what keeps it from reopening the post-final window.
    private func reapFinishedStreams() {
        lock.lock()
        var finished: [Unmanaged<StreamContext>] = []
        streams.removeAll { stream in
            guard stream.takeUnretainedValue().hasFinished else { return false }
            finished.append(stream)
            return true
        }
        lock.unlock()
        // Released outside the lock: nothing else needs to wait on a deallocation.
        for stream in finished { stream.release() }
    }

    /// Builds the per-send optional args. A repetition penalty of <= 1.0 and an n-gram size of
    /// <= 0 mean "guard off" and are simply not set — identical semantics to the Android path,
    /// which is what lets the settings-sheet knob turn the guards off.
    ///
    /// Ownership passes to the StreamContext, which deletes it in `deinit`.
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
