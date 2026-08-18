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

package com.google.ai.edge.gallery.inference

import io.github.aakira.napier.Napier

private const val TAG = "IosLlmEngine"

/**
 * Protocol that Swift code implements to provide LLM inference.
 *
 * Implemented by `CraneLlmBridge.swift`, which drives the LiteRT-LM v0.16 C API
 * (`CLiteRTLM.xcframework`) directly — the iOS counterpart of Android's `crane_llm_jni.c`.
 * Android needs a C shim because the JVM cannot call C; Swift can, so the Swift file is the
 * shim. Keeping it behind this delegate rather than a Kotlin/Native cinterop is deliberate:
 * the C API hands stream chunks back on its own thread, which Swift handles with a plain
 * `@convention(c)` callback, and the published xcframework ships arm64 slices only — a cinterop
 * would break the `iosX64()` target, which has no slice to link against.
 */
interface IosLlmDelegate {
  fun initialize(
    modelPath: String,
    backend: String,
    maxTokens: Int,
    cacheDir: String?,
    onDone: (error: String) -> Unit,
  )

  fun createConversation(
    topK: Int,
    topP: Double,
    temperature: Double,
    systemInstruction: String?,
  ): IosLlmConversationDelegate

  fun close()
}

/**
 * Protocol that Swift code implements for a conversation session.
 *
 * The send functions carry the Crane decoding guards explicitly ([repetitionPenalty],
 * [noRepeatNgramSize]) rather than letting the Swift side pick its own: they originate in
 * [LlmGenerationOptions] on the shared seam, so both platforms read the same per-model config
 * and the settings sheet can turn them off. `repetitionPenalty <= 1.0` and
 * `noRepeatNgramSize <= 0` mean "guard disabled", matching the Android C-API path.
 */
interface IosLlmConversationDelegate {
  fun sendMessageAsync(
    text: String,
    imageBytes: List<ByteArray>,
    audioBytes: List<ByteArray>,
    maxOutputTokens: Int,
    repetitionPenalty: Float,
    noRepeatNgramSize: Int,
    onToken: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  )

  fun sendMessage(
    text: String,
    imageBytes: List<ByteArray>,
    audioBytes: List<ByteArray>,
    maxOutputTokens: Int,
    repetitionPenalty: Float,
    noRepeatNgramSize: Int,
  ): String

  fun cancel()
  fun close()
}

/**
 * iOS implementation of [LlmInferenceEngine] that delegates to a Swift-provided
 * [IosLlmDelegate]. The delegate is injected at app startup from the Swift side.
 */
class IosLlmInferenceEngine(private val delegate: IosLlmDelegate) : LlmInferenceEngine {

  override fun initialize(config: LlmEngineConfig, onDone: (error: String) -> Unit) {
    val backendStr = when (config.backend) {
      LlmBackend.CPU -> "cpu"
      LlmBackend.GPU -> "gpu"
    }
    delegate.initialize(
      modelPath = config.modelPath,
      backend = backendStr,
      maxTokens = config.maxNumTokens,
      cacheDir = config.cacheDir,
      onDone = onDone,
    )
  }

  override fun createConversation(config: LlmConversationConfig): LlmConversation {
    val systemText = config.systemInstruction
      ?.filterIsInstance<LlmContent.Text>()
      ?.joinToString("\n") { it.text }

    val conversationDelegate = delegate.createConversation(
      topK = config.topK,
      topP = config.topP,
      temperature = config.temperature,
      systemInstruction = systemText,
    )
    return IosLlmConversation(conversationDelegate)
  }

  override fun close() {
    delegate.close()
  }
}

/**
 * iOS implementation of [LlmConversation] delegating to the Swift-provided
 * [IosLlmConversationDelegate].
 */
class IosLlmConversation(
  private val delegate: IosLlmConversationDelegate,
) : LlmConversation {

  /**
   * The Crane decoding guards in [options] are applied per-send, forwarded to the Swift
   * [CraneLlmBridge] which sets `repetition_penalty` + `no_repeat_ngram` on the LiteRT-LM
   * conversation's optional args — the same C-API calls `crane_llm_jni.c` makes on Android.
   */
  override fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions,
  ) {
    val text = contents.filterIsInstance<LlmContent.Text>().joinToString("\n") { it.text }
    val images = contents.filterIsInstance<LlmContent.ImageBytes>().map { it.bytes }
    val audio = contents.filterIsInstance<LlmContent.AudioBytes>().map { it.bytes }

    Napier.d(tag = TAG) {
      "Send with guards: maxTokens=${options.maxOutputTokens} " +
        "rep=${options.repetitionPenalty} ngram=${options.noRepeatNgramSize}"
    }

    delegate.sendMessageAsync(
      text = text,
      imageBytes = images,
      audioBytes = audio,
      maxOutputTokens = options.maxOutputTokens,
      repetitionPenalty = options.repetitionPenalty,
      noRepeatNgramSize = options.noRepeatNgramSize,
      onToken = { token -> callback.onMessage(token) },
      onDone = { callback.onDone() },
      onError = { errorMsg -> callback.onError(RuntimeException(errorMsg)) },
    )
  }

  /** Guards apply per-send here too; see [sendMessageAsync]. */
  override fun sendMessage(contents: List<LlmContent>, options: LlmGenerationOptions): String {
    val text = contents.filterIsInstance<LlmContent.Text>().joinToString("\n") { it.text }
    val images = contents.filterIsInstance<LlmContent.ImageBytes>().map { it.bytes }
    val audio = contents.filterIsInstance<LlmContent.AudioBytes>().map { it.bytes }
    return delegate.sendMessage(
      text = text,
      imageBytes = images,
      audioBytes = audio,
      maxOutputTokens = options.maxOutputTokens,
      repetitionPenalty = options.repetitionPenalty,
      noRepeatNgramSize = options.noRepeatNgramSize,
    )
  }

  override fun cancelProcess() {
    delegate.cancel()
  }

  override fun close() {
    delegate.close()
  }
}
