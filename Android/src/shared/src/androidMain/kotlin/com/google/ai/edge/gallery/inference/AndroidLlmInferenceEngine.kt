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

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.aakira.napier.Napier

private const val TAG = "AndroidLlmEngine"

/**
 * Android implementation of [LlmInferenceEngine] wrapping LiteRT-LM's Engine.
 */
@OptIn(ExperimentalApi::class)
class AndroidLlmInferenceEngine : LlmInferenceEngine {
  private var engine: Engine? = null

  override fun initialize(config: LlmEngineConfig, onDone: (error: String) -> Unit) {
    try {
      val backend = when (config.backend) {
        LlmBackend.CPU -> Backend.CPU
        LlmBackend.GPU -> Backend.GPU
      }
      val visionBackend = config.visionBackend?.let {
        when (it) {
          LlmBackend.CPU -> Backend.CPU
          LlmBackend.GPU -> Backend.GPU
        }
      }
      val audioBackend = config.audioBackend?.let {
        when (it) {
          LlmBackend.CPU -> Backend.CPU
          LlmBackend.GPU -> Backend.GPU
        }
      }
      val engineConfig = EngineConfig(
        modelPath = config.modelPath,
        backend = backend,
        visionBackend = visionBackend,
        audioBackend = audioBackend,
        maxNumTokens = config.maxNumTokens,
        cacheDir = config.cacheDir,
      )
      engine = Engine(engineConfig)
      engine!!.initialize()
      onDone("")
    } catch (e: Exception) {
      Napier.e(tag = TAG) { "Failed to initialize engine: ${e.message}" }
      onDone(e.message ?: "Unknown error during initialization")
    }
  }

  override fun createConversation(config: LlmConversationConfig): LlmConversation {
    val engine = this.engine ?: throw IllegalStateException("Engine not initialized")

    val systemInstruction = config.systemInstruction?.let { contents ->
      Contents.of(contents.map { it.toLiteRTContent() })
    }

    val conversation = engine.createConversation(
      ConversationConfig(
        samplerConfig = SamplerConfig(
          topK = config.topK,
          topP = config.topP,
          temperature = config.temperature,
        ),
        systemInstruction = systemInstruction,
        tools = config.tools,
      )
    )
    return AndroidLlmConversation(conversation)
  }

  override fun close() {
    engine?.close()
    engine = null
  }
}

/**
 * Android implementation of [LlmConversation] wrapping LiteRT-LM's Conversation.
 */
@OptIn(ExperimentalApi::class)
class AndroidLlmConversation(private val conversation: Conversation) : LlmConversation {

  override fun sendMessageAsync(
    contents: List<LlmContent>,
    callback: LlmMessageCallback,
    options: LlmGenerationOptions,
  ) {
    // The litertlm AAR predates the repetition-penalty / no-repeat-ngram APIs (that's the
    // whole reason the Crane engine exists), so [options]'s guards are a no-op here.
    val liteRTContents = Contents.of(contents.map { it.toLiteRTContent() })
    conversation.sendMessageAsync(
      liteRTContents,
      object : MessageCallback {
        override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
          callback.onMessage(message.toString())
        }

        override fun onDone() {
          callback.onDone()
        }

        override fun onError(throwable: Throwable) {
          callback.onError(throwable)
        }
      },
    )
  }

  override fun sendMessage(contents: List<LlmContent>, options: LlmGenerationOptions): String {
    // No-op guards; see sendMessageAsync.
    val liteRTContents = Contents.of(contents.map { it.toLiteRTContent() })
    val message = conversation.sendMessage(liteRTContents)
    return message.toString()
  }

  override fun cancelProcess() {
    conversation.cancelProcess()
  }

  override fun close() {
    conversation.close()
  }
}

/** Convert shared LlmContent to LiteRT-LM Content. */
private fun LlmContent.toLiteRTContent(): Content {
  return when (this) {
    is LlmContent.Text -> Content.Text(text)
    is LlmContent.ImageBytes -> Content.ImageBytes(bytes)
    is LlmContent.AudioBytes -> Content.AudioBytes(bytes)
  }
}
