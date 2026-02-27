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
  fun sendMessageAsync(contents: List<LlmContent>, callback: LlmMessageCallback)
  fun sendMessage(contents: List<LlmContent>): String
  fun cancelProcess()
  fun close()
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
