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

/**
 * Platform-agnostic interface for LLM inference engines.
 *
 * On Android, this wraps LiteRT-LM's Engine.
 * On iOS, this wraps the MediaPipe iOS SDK via a Swift delegate.
 */
interface LlmInferenceEngine {
  fun initialize(config: LlmEngineConfig, onDone: (error: String) -> Unit)
  fun createConversation(config: LlmConversationConfig): LlmConversation
  fun close()
}

/** A conversation session with an LLM engine. */
interface LlmConversation {
  fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions = LlmGenerationOptions(),
  )

  fun sendMessage(contents: List<LlmContent>, options: LlmGenerationOptions = LlmGenerationOptions()): String

  fun cancelProcess()
  fun close()
}

/**
 * Options controlling a single generation call.
 *
 * [repetitionPenalty] and [noRepeatNgramSize] are the Crane decoding guards that stop
 * open-ended prompts from doom-looping. Only the androidMain Crane engine (the JNI bridge over
 * the LiteRT-LM v0.16 C API) honors them today; engines that don't support them — the stock
 * AAR-backed [AndroidLlmInferenceEngine], and iOS until its C-API cinterop lands — treat them
 * as a no-op.
 *
 * @param maxOutputTokens Cap on generated tokens for this send; <= 0 means "use the engine's
 *   default".
 * @param repetitionPenalty <= 1.0 disables the penalty (1.0 is a no-op by definition).
 * @param noRepeatNgramSize <= 0 disables the no-repeat-ngram guard.
 */
data class LlmGenerationOptions(
  val maxOutputTokens: Int = 0,
  val repetitionPenalty: Float = DEFAULT_REPETITION_PENALTY,
  val noRepeatNgramSize: Int = DEFAULT_NO_REPEAT_NGRAM_SIZE,
) {
  companion object {
    // Crane serving defaults, proven on-device: stop the doom-loops the model exhibits
    // under unguarded greedy decoding.
    const val DEFAULT_REPETITION_PENALTY = 1.15f
    const val DEFAULT_NO_REPEAT_NGRAM_SIZE = 3
  }
}

/** Configuration for initializing the inference engine. */
data class LlmEngineConfig(
  val modelPath: String,
  val backend: LlmBackend,
  val visionBackend: LlmBackend? = null,
  val audioBackend: LlmBackend? = null,
  val maxNumTokens: Int,
  val cacheDir: String? = null,
)

/** Configuration for creating a conversation. */
data class LlmConversationConfig(
  val topK: Int,
  val topP: Double,
  val temperature: Double,
  val systemInstruction: List<LlmContent>? = null,
  val tools: List<Any> = listOf(),
  val enableConstrainedDecoding: Boolean = false,
)

/** Content that can be sent as part of a message. */
sealed class LlmContent {
  data class Text(val text: String) : LlmContent()
  data class ImageBytes(val bytes: ByteArray) : LlmContent()
  data class AudioBytes(val bytes: ByteArray) : LlmContent()
}

/** Compute backend selection. */
enum class LlmBackend {
  CPU,
  GPU,
}

/** Callback for receiving streaming inference results. */
interface LlmMessageCallback {
  fun onMessage(text: String)
  fun onDone()
  fun onError(throwable: Throwable)
}
