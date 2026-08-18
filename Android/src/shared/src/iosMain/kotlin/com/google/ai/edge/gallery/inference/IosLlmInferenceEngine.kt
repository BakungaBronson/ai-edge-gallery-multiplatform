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
 * Protocol that Swift code implements to provide LLM inference via
 * MediaPipe iOS SDK (or any other iOS-native inference backend).
 *
 * This avoids direct Kotlin/Native cinterop with MediaPipe's Objective-C API,
 * which can cause NPEs (as documented by the MediaPiper reference project).
 * Instead, the Swift side handles all MediaPipe calls and exposes them
 * through this delegate interface.
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
 */
interface IosLlmConversationDelegate {
  fun sendMessageAsync(
    text: String,
    imageBytes: List<ByteArray>,
    audioBytes: List<ByteArray>,
    onToken: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  )

  fun sendMessage(
    text: String,
    imageBytes: List<ByteArray>,
    audioBytes: List<ByteArray>,
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

  // TODO(crane): [options]'s repetitionPenalty/noRepeatNgramSize are NOT applied on iOS yet.
  // The MediaPipe iOS SDK behind [delegate] predates those decoding guards, same as the
  // Android AAR they replace on Android. Do not present these as active on iOS — see
  // CraneLlmInferenceEngine (androidMain) for the C-API path that will eventually get an
  // iOS cinterop actual over the same LiteRT-LM C API.
  override fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions,
  ) {
    val text = contents.filterIsInstance<LlmContent.Text>().joinToString("\n") { it.text }
    val images = contents.filterIsInstance<LlmContent.ImageBytes>().map { it.bytes }
    val audio = contents.filterIsInstance<LlmContent.AudioBytes>().map { it.bytes }

    delegate.sendMessageAsync(
      text = text,
      imageBytes = images,
      audioBytes = audio,
      onToken = { token -> callback.onMessage(token) },
      onDone = { callback.onDone() },
      onError = { errorMsg -> callback.onError(RuntimeException(errorMsg)) },
    )
  }

  // TODO(crane): guards not applied on iOS yet; see sendMessageAsync above.
  override fun sendMessage(contents: List<LlmContent>, options: LlmGenerationOptions): String {
    val text = contents.filterIsInstance<LlmContent.Text>().joinToString("\n") { it.text }
    val images = contents.filterIsInstance<LlmContent.ImageBytes>().map { it.bytes }
    val audio = contents.filterIsInstance<LlmContent.AudioBytes>().map { it.bytes }
    return delegate.sendMessage(text = text, imageBytes = images, audioBytes = audio)
  }

  override fun cancelProcess() {
    delegate.cancel()
  }

  override fun close() {
    delegate.close()
  }
}
