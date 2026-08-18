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

package com.google.ai.edge.gallery.llm

import android.util.Log
import org.json.JSONObject

private const val TAG = "CraneLlm"

/**
 * Thin JNI bridge to the LiteRT-LM v0.16 C-API (`liblitert-lm.so`), reached through the small
 * `libcrane_llm_jni.so` shim built from `crane_llm_jni.c` (repo root).
 *
 * This bypasses the litertlm Kotlin AAR ([com.google.ai.edge.litertlm.Engine]) so that Crane's
 * decoding guards (repetition penalty + no-repeat-ngram) and a template-safe system prompt can
 * be applied — neither is possible through the AAR, which predates both APIs and returns
 * instant empty responses when a system prompt is set (see [CraneLlmInferenceEngine]).
 *
 * IMPORTANT: this object's package + name (`com.google.ai.edge.gallery.llm.CraneLlm`) is baked
 * into the prebuilt `libcrane_llm_jni.so`'s exported `Java_com_google_ai_edge_gallery_llm_...`
 * symbols. Renaming or moving this object requires recompiling that .so (see repo-root
 * `crane_llm_jni.c` and the arm64-host build recipe in its header comment).
 */
object CraneLlm {
  // Crane serving defaults, proven on-device: stop the doom-loops the model exhibits under
  // unguarded greedy decoding.
  const val REPETITION_PENALTY = 1.15f
  const val NO_REPEAT_NGRAM_SIZE = 3

  private var loaded = false

  init {
    try {
      System.loadLibrary("crane_llm_jni")
      loaded = true
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to load crane_llm_jni", t)
    }
  }

  /** Whether the native inference library loaded successfully. */
  fun isAvailable(): Boolean = loaded

  /** Creates an engine for the model file; returns 0 on failure. */
  fun createEngine(modelPath: String): Long =
    nativeCreateEngine(modelPath.toByteArray(Charsets.UTF_8))

  fun deleteEngine(engineHandle: Long) = nativeDeleteEngine(engineHandle)

  /**
   * Creates a conversation, applying [systemPrompt] template-safely at creation time
   * (null/blank = no system prompt); returns 0 on failure.
   */
  fun createConversation(engineHandle: Long, systemPrompt: String?): Long {
    val systemJson =
      if (systemPrompt.isNullOrBlank()) null
      else
        JSONObject()
          .put("role", "system")
          .put("content", systemPrompt)
          .toString()
          .toByteArray(Charsets.UTF_8)
    return nativeCreateConversation(engineHandle, systemJson)
  }

  fun deleteConversation(conversationHandle: Long) = nativeDeleteConversation(conversationHandle)

  /** Cancels an in-flight generation on the conversation. */
  fun cancel(conversationHandle: Long) = nativeCancel(conversationHandle)

  /** Prefill token count of the most recent turn (0 if unavailable). */
  fun lastPrefillTokenCount(conversationHandle: Long): Int =
    nativeLastPrefillTokenCount(conversationHandle)

  /**
   * The C-API streams each chunk as a JSON message string like
   * {"role":"assistant","content":[{"type":"text","text":"..."}]}. Extract the plain text; fall
   * back to the raw string for non-JSON chunks.
   */
  fun extractChunkText(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return raw
    return try {
      val obj = JSONObject(trimmed)
      val content = obj.opt("content")
      when (content) {
        is org.json.JSONArray -> {
          val sb = StringBuilder()
          for (i in 0 until content.length()) {
            val part = content.optJSONObject(i)
            if (part != null && part.has("text")) sb.append(part.optString("text"))
          }
          sb.toString()
        }
        is String -> content
        else -> raw
      }
    } catch (e: Exception) {
      raw
    }
  }

  /**
   * Sends a user message and streams the response via [callback]. Blocks the calling thread
   * until the final chunk, so call from a background dispatcher. Decoding guards apply
   * per-send: [repetitionPenalty] <= 1.0 or [noRepeatNgramSize] <= 0 disables the respective
   * guard. Returns 0 on success.
   */
  fun sendMessageStream(
    conversationHandle: Long,
    userText: String,
    maxOutputTokens: Int,
    callback: ChunkCallback,
    repetitionPenalty: Float = REPETITION_PENALTY,
    noRepeatNgramSize: Int = NO_REPEAT_NGRAM_SIZE,
  ): Int {
    val messageJson =
      JSONObject()
        .put("role", "user")
        .put("content", userText)
        .toString()
        .toByteArray(Charsets.UTF_8)
    // Unwrap the per-chunk JSON message envelope before handing text to the caller.
    val unwrapping =
      object : ChunkCallback {
        override fun onChunk(textBytes: ByteArray?, done: Boolean, errorBytes: ByteArray?) {
          val plain =
            textBytes?.let { extractChunkText(String(it, Charsets.UTF_8)) }?.toByteArray(Charsets.UTF_8)
          callback.onChunk(plain, done, errorBytes)
        }
      }
    return nativeSendMessageStream(
      conversationHandle,
      messageJson,
      maxOutputTokens,
      // <= 1.0 / <= 0 disable the guards in the JNI layer (it only sets the configs for
      // values > 0; a 1.0 penalty is a no-op anyway, but skip it explicitly for clarity).
      if (repetitionPenalty > 1.0f) repetitionPenalty else 0f,
      noRepeatNgramSize,
      unwrapping,
    )
  }

  /** Streaming callback; text/error arrive as UTF-8 bytes. */
  interface ChunkCallback {
    fun onChunk(textBytes: ByteArray?, done: Boolean, errorBytes: ByteArray?)
  }

  private external fun nativeCreateEngine(modelPathBytes: ByteArray): Long

  private external fun nativeDeleteEngine(engineHandle: Long)

  private external fun nativeCreateConversation(
    engineHandle: Long,
    systemMessageJsonBytes: ByteArray?,
  ): Long

  private external fun nativeDeleteConversation(conversationHandle: Long)

  private external fun nativeCancel(conversationHandle: Long)

  private external fun nativeLastPrefillTokenCount(conversationHandle: Long): Int

  private external fun nativeSendMessageStream(
    conversationHandle: Long,
    messageJsonBytes: ByteArray,
    maxOutputTokens: Int,
    repetitionPenalty: Float,
    noRepeatNgramSize: Int,
    callback: ChunkCallback,
  ): Int
}
