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

import com.google.ai.edge.gallery.inference.CraneLlmInferenceEngine
import com.google.ai.edge.gallery.inference.LlmContent
import com.google.ai.edge.gallery.inference.LlmConversation
import com.google.ai.edge.gallery.inference.LlmGenerationOptions
import com.google.ai.edge.gallery.inference.LlmMessageCallback
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// CRANE_SYSTEM_PROMPT now lives in commonMain (CraneServingDefaults.kt, same package), so it's
// visible here without an import — :app depends on :shared and both are
// com.google.ai.edge.gallery.llm. Moved so the settings-sheet config default (commonMain) and
// this Android chat path share one definition instead of two copies drifting.

/**
 * Compat wrapper around [CraneLlmInferenceEngine]'s KMP seam, shaped like the litertlm AAR's
 * `Engine` (just a `close()`) so [LlmModelInstance][com.google.ai.edge.gallery.ui.llmchat
 * .LlmModelInstance] doesn't need an AAR-typed `engine` field.
 */
class CraneEngine(val inferenceEngine: CraneLlmInferenceEngine) {
  fun close() = inferenceEngine.close()
}

/**
 * Compat wrapper around a seam [LlmConversation], shaped like the litertlm AAR's `Conversation`
 * (`Contents` in, `Message`/`Flow<Message>` out) so the custom-task view models (TinyGarden,
 * MobileActions), which call `conversation.sendMessage(Contents)` /
 * `conversation.sendMessageAsync(Contents): Flow<Message>` directly against the AAR shape, don't
 * need to change. [options] defaults to the Crane serving guards (1.15 / 3); the chat path
 * (LlmChatModelHelper.runInference) passes its own per-model-config [options] explicitly instead
 * of relying on this default.
 */
class CraneConversation(private val conversation: LlmConversation) {
  fun cancelProcess() = conversation.cancelProcess()

  fun close() = conversation.close()

  /**
   * Direct passthrough to the underlying seam [LlmConversation], for callers that already work
   * in seam types and don't need the litertlm-shaped Contents/Message API — namely the main chat
   * path (LlmChatModelHelper.runInference), which mirrors the wrapper's own design of talking to
   * the bridge directly rather than round-tripping through litertlm types.
   */
  fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions = LlmGenerationOptions(),
  ) = conversation.sendMessageAsync(contents, callback, options)

  /** Blocking compat for `conversation.sendMessage(Contents): Message`. */
  fun sendMessage(contents: Contents, options: LlmGenerationOptions = LlmGenerationOptions()): Message {
    val text = conversation.sendMessage(contents.toLlmContents(), options)
    return Message.of(text)
  }

  /** Streaming compat for `conversation.sendMessageAsync(Contents): Flow<Message>`. */
  fun sendMessageAsync(
    contents: Contents,
    options: LlmGenerationOptions = LlmGenerationOptions(),
  ): Flow<Message> = callbackFlow {
    conversation.sendMessageAsync(
      contents.toLlmContents(),
      object : LlmMessageCallback {
        override fun onMessage(text: String) {
          trySendBlocking(Message.of(text))
        }

        override fun onDone() {
          close()
        }

        override fun onError(throwable: Throwable) {
          close(throwable)
        }
      },
      options,
    )
    awaitClose {}
  }
}

/** Converts litertlm AAR [Contents] to the seam's [LlmContent] list. Also used by
 * [com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper] to convert a caller-supplied
 * system-instruction `Contents` before creating a Crane conversation. */
internal fun Contents.toLlmContents(): List<LlmContent> =
  contents.mapNotNull { content ->
    when (content) {
      is Content.Text -> LlmContent.Text(content.text)
      is Content.ImageBytes -> LlmContent.ImageBytes(content.bytes)
      is Content.AudioBytes -> LlmContent.AudioBytes(content.bytes)
      // ImageFile/AudioFile/ToolResponse: not produced by any current caller (chat, TinyGarden,
      // MobileActions all send Text, and chat's image/audio tasks send *Bytes); the Crane C-API
      // path is text-only regardless (see CraneLlmInferenceEngine).
      else -> null
    }
  }
