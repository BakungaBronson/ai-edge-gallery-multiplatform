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

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.inference.CraneLlmInferenceEngine
import com.google.ai.edge.gallery.inference.LlmBackend
import com.google.ai.edge.gallery.inference.LlmContent
import com.google.ai.edge.gallery.inference.LlmConversationConfig
import com.google.ai.edge.gallery.inference.LlmEngineConfig
import com.google.ai.edge.gallery.inference.LlmGenerationOptions
import com.google.ai.edge.gallery.inference.LlmMessageCallback
import com.google.ai.edge.gallery.llm.CRANE_SYSTEM_PROMPT
import com.google.ai.edge.gallery.llm.CraneConversation
import com.google.ai.edge.gallery.llm.CraneEngine
import com.google.ai.edge.gallery.llm.toLlmContents
import com.google.ai.edge.litertlm.Contents

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: CraneEngine, var conversation: CraneConversation)

/**
 * Crane fork: inference is routed through the LiteRT-LM v0.16 C-API (see
 * [CraneLlmInferenceEngine]) instead of the litertlm Kotlin AAR. This applies the decoding
 * guards (repetition penalty + no-repeat-ngram, defaults 1.15 / 3) and a template-safe system
 * prompt — both proven on-device and impossible through the stock AAR path, which predates the
 * guard APIs and returns instant empty responses when a system prompt is set.
 */
object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  /**
   * Resolves the system instruction to apply: the caller-supplied [systemInstruction] (custom
   * tasks like TinyGarden/MobileActions always pass one), or else the per-model configured
   * system prompt, defaulting to the Crane serving prompt (chat/ask-image/ask-audio don't pass
   * one today; the settings-sheet row to override it per model lands in crane/settings-ui).
   */
  private fun systemInstructionContents(model: Model, systemInstruction: Contents?): List<LlmContent> {
    if (systemInstruction != null) return systemInstruction.toLlmContents()
    val promptText =
      model.getStringConfigValue(key = ConfigKeys.SYSTEM_PROMPT, defaultValue = CRANE_SYSTEM_PROMPT)
    return if (promptText.isBlank()) listOf() else listOf(LlmContent.Text(promptText))
  }

  private fun generationOptions(model: Model): LlmGenerationOptions {
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    // Decoding guards: read from the per-model config (Model configs sheet), defaulting to the
    // Crane serving values. Applied per-send, so changes take effect on the next message
    // without re-initialization.
    val repetitionPenalty =
      model.getFloatConfigValue(
        key = ConfigKeys.REPETITION_PENALTY,
        defaultValue = LlmGenerationOptions.DEFAULT_REPETITION_PENALTY,
      )
    val noRepeatNgramSize =
      model.getIntConfigValue(
        key = ConfigKeys.NO_REPEAT_NGRAM,
        defaultValue = LlmGenerationOptions.DEFAULT_NO_REPEAT_NGRAM_SIZE,
      )
    return LlmGenerationOptions(
      maxOutputTokens = maxTokens,
      repetitionPenalty = repetitionPenalty,
      noRepeatNgramSize = noRepeatNgramSize,
    )
  }

  fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents? = null,
    tools: List<Any> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
  ) {
    Log.d(TAG, "Initializing (Crane C-API path)...")
    // The Crane C-API path is text-only and CPU-only for now (see CraneLlmInferenceEngine), so
    // supportImage/supportAudio don't change engine setup here — kept for call-site parity with
    // the AAR-backed API this replaces.
    val accelerator =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val backend = if (accelerator == Accelerator.GPU.label) LlmBackend.GPU else LlmBackend.CPU
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val modelPath = model.getPath(basePath = context.getExternalFilesDir(null)?.absolutePath ?: "")

    val inferenceEngine = CraneLlmInferenceEngine()
    var initError = ""
    inferenceEngine.initialize(
      LlmEngineConfig(modelPath = modelPath, backend = backend, maxNumTokens = maxTokens)
    ) { error ->
      initError = error
    }
    if (initError.isNotEmpty()) {
      onDone(cleanUpMediapipeTaskErrorMessage(initError))
      return
    }

    try {
      val conversation =
        inferenceEngine.createConversation(
          LlmConversationConfig(
            topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK),
            topP =
              model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP).toDouble(),
            temperature =
              model
                .getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
                .toDouble(),
            systemInstruction = systemInstructionContents(model, systemInstruction),
            tools = tools,
            enableConstrainedDecoding = enableConversationConstrainedDecoding,
          )
        )
      model.instance =
        LlmModelInstance(
          engine = CraneEngine(inferenceEngine),
          conversation = CraneConversation(conversation),
        )
    } catch (e: Exception) {
      onDone("Error initializing model: ${e.message}")
      return
    }
    onDone("")
  }

  fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents? = null,
    tools: List<Any> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      instance.conversation.close()

      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)

      val newConversation =
        instance.engine.inferenceEngine.createConversation(
          LlmConversationConfig(
            topK = topK,
            topP = topP.toDouble(),
            temperature = temperature.toDouble(),
            systemInstruction = systemInstructionContents(model, systemInstruction),
            tools = tools,
            enableConstrainedDecoding = enableConversationConstrainedDecoding,
          )
        )
      instance.conversation = CraneConversation(newConversation)

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset conversation", e)
    }
  }

  fun cleanUp(model: Model, onDone: () -> Unit) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit = {},
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
  ) {
    val instance = model.instance as LlmModelInstance

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    val conversation = instance.conversation

    if (images.isNotEmpty() || audioClips.isNotEmpty()) {
      Log.w(TAG, "Crane C-API path is text-only; ignoring images/audio")
    }
    val contents = mutableListOf<LlmContent>()
    if (input.trim().isNotEmpty()) {
      contents.add(LlmContent.Text(input))
    }

    val options = generationOptions(model)
    Log.d(
      TAG,
      "Send with guards: maxTokens=${options.maxOutputTokens} rep=${options.repetitionPenalty} " +
        "ngram=${options.noRepeatNgramSize}",
    )

    try {
      conversation.sendMessageAsync(
        contents,
        object : LlmMessageCallback {
          override fun onMessage(text: String) {
            if (text.isNotEmpty()) resultListener(text, false)
          }

          override fun onDone() {
            resultListener("", true)
          }

          override fun onError(throwable: Throwable) {
            Log.e(TAG, "onError", throwable)
            onError("Error: ${throwable.message}")
          }
        },
        options,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Inference error", e)
      onError("Error: ${e.message}")
    }
  }
}
