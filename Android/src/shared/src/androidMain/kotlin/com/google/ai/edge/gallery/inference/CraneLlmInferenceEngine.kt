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

import com.google.ai.edge.gallery.llm.CraneLlm
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "CraneLlmEngine"

/**
 * Android implementation of [LlmInferenceEngine] wrapping [CraneLlm], a JNI bridge over the
 * LiteRT-LM v0.16 C API. This is the fix for the two bugs the litertlm AAR has (see
 * [AndroidLlmInferenceEngine]): no decoding-guard API (doom loops on open-ended prompts) and
 * empty responses when a system prompt is set.
 *
 * The C-API path is text-only and CPU-only for now: [LlmEngineConfig.backend]/visionBackend/
 * audioBackend/maxNumTokens/cacheDir are not supported by the native bridge and are ignored;
 * non-text [LlmContent] is dropped with a log line, same as the wrapper this was ported from.
 */
class CraneLlmInferenceEngine : LlmInferenceEngine {
  private var engineHandle: Long = 0

  override fun initialize(config: LlmEngineConfig, onDone: (error: String) -> Unit) {
    if (!CraneLlm.isAvailable()) {
      onDone("Crane LiteRT-LM native library is not available on this device")
      return
    }
    if (config.backend == LlmBackend.GPU) {
      Napier.w(tag = TAG) { "Crane C-API path is CPU-only; ignoring requested GPU backend" }
    }
    try {
      val handle = CraneLlm.createEngine(config.modelPath)
      if (handle == 0L) {
        onDone("Failed to load model: ${config.modelPath}")
        return
      }
      engineHandle = handle
    } catch (e: Exception) {
      Napier.e(tag = TAG) { "Failed to initialize engine: ${e.message}" }
      onDone(e.message ?: "Unknown error during initialization")
      return
    }
    onDone("")
  }

  override fun createConversation(config: LlmConversationConfig): LlmConversation {
    val handle = engineHandle
    if (handle == 0L) throw IllegalStateException("Engine not initialized")

    // The C-API applies the system prompt template-safely at conversation creation; only the
    // text content is used (the AAR path this replaces has the same system-prompt-only
    // limitation for non-text content).
    val systemText =
      config.systemInstruction
        ?.filterIsInstance<LlmContent.Text>()
        ?.joinToString("\n") { it.text }

    val convHandle = CraneLlm.createConversation(handle, systemText)
    if (convHandle == 0L) throw IllegalStateException("Failed to create conversation")
    return CraneLlmConversation(convHandle)
  }

  override fun close() {
    val handle = engineHandle
    engineHandle = 0
    if (handle != 0L) CraneLlm.deleteEngine(handle)
  }
}

/**
 * Android implementation of [LlmConversation] wrapping a Crane C-API conversation handle.
 */
class CraneLlmConversation(private val handle: Long) : LlmConversation {

  override fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions,
  ) {
    // The native call blocks the calling thread until the final chunk, so run it on a
    // background dispatcher to give callers a genuinely async API.
    CoroutineScope(Dispatchers.IO).launch {
      var sawError = false
      val rc =
        CraneLlm.sendMessageStream(
          conversationHandle = handle,
          userText = contentsToText(contents),
          maxOutputTokens = options.maxOutputTokens,
          repetitionPenalty = options.repetitionPenalty,
          noRepeatNgramSize = options.noRepeatNgramSize,
          callback =
            object : CraneLlm.ChunkCallback {
              override fun onChunk(textBytes: ByteArray?, done: Boolean, errorBytes: ByteArray?) {
                if (errorBytes != null) {
                  sawError = true
                  callback.onError(RuntimeException(String(errorBytes, Charsets.UTF_8)))
                  return
                }
                val text = textBytes?.let { String(it, Charsets.UTF_8) }
                if (!text.isNullOrEmpty()) callback.onMessage(text)
                if (done) callback.onDone()
              }
            },
        )
      if (rc != 0 && !sawError) {
        callback.onError(RuntimeException("inference failed to start (rc=$rc)"))
      }
    }
  }

  override fun sendMessage(contents: List<LlmContent>, options: LlmGenerationOptions): String {
    val sb = StringBuilder()
    var error: String? = null
    val rc =
      CraneLlm.sendMessageStream(
        conversationHandle = handle,
        userText = contentsToText(contents),
        maxOutputTokens = options.maxOutputTokens,
        repetitionPenalty = options.repetitionPenalty,
        noRepeatNgramSize = options.noRepeatNgramSize,
        callback =
          object : CraneLlm.ChunkCallback {
            override fun onChunk(textBytes: ByteArray?, done: Boolean, errorBytes: ByteArray?) {
              if (errorBytes != null) error = String(errorBytes, Charsets.UTF_8)
              textBytes?.let { sb.append(String(it, Charsets.UTF_8)) }
            }
          },
      )
    if (rc != 0 || error != null) {
      throw RuntimeException(error ?: "inference failed (rc=$rc)")
    }
    return sb.toString()
  }

  override fun cancelProcess() = CraneLlm.cancel(handle)

  override fun close() = CraneLlm.deleteConversation(handle)

  private fun contentsToText(contents: List<LlmContent>): String {
    val nonText = contents.count { it !is LlmContent.Text }
    if (nonText > 0) {
      Napier.w(tag = TAG) { "Crane C-API path is text-only; ignoring $nonText non-text content item(s)" }
    }
    return contents.filterIsInstance<LlmContent.Text>().joinToString("\n") { it.text }
  }
}
