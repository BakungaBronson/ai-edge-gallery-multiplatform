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

package com.google.ai.edge.gallery.customtasks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.inference.LlmBackend
import com.google.ai.edge.gallery.inference.LlmEngineConfig
import com.google.ai.edge.gallery.inference.LlmInferenceEngine
import com.google.ai.edge.gallery.platform.PlatformContext
import com.google.ai.edge.gallery.platform.getAppFilesDirectory
import com.google.ai.edge.gallery.platform.getCacheDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope

private const val TAG = "IosCustomTasks"

/**
 * Base class for iOS LLM tasks.
 *
 * Provides shared initialization and cleanup logic using [LlmInferenceEngine].
 * On iOS, the task's [MainScreen] is not used — [IosScreenProvider] renders
 * [IosModelScreen] instead. The init/cleanup functions are used if the
 * standard [ModelManagerActions.initializeModel] path goes through custom tasks.
 */
abstract class IosLlmTask(
  private val engine: LlmInferenceEngine,
) : CustomTask {

  override fun initializeModelFn(
    context: PlatformContext,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: (error: String) -> Unit,
  ) {
    val basePath = context.getAppFilesDirectory()
    val modelPath = model.getPath(basePath = basePath)
    val cacheDir = context.getCacheDirectory()

    val maxTokens = model.getIntConfigValue(
      key = ConfigKeys.MAX_TOKENS,
      defaultValue = if (model.llmMaxToken > 0) model.llmMaxToken else DEFAULT_MAX_TOKEN,
    )
    val acceleratorLabel = model.getStringConfigValue(
      key = ConfigKeys.ACCELERATOR,
      defaultValue = Accelerator.GPU.label,
    )
    val backend = when (acceleratorLabel) {
      Accelerator.CPU.label -> LlmBackend.CPU
      else -> LlmBackend.GPU
    }

    val config = LlmEngineConfig(
      modelPath = modelPath,
      backend = backend,
      maxNumTokens = maxTokens,
      cacheDir = cacheDir,
    )

    Napier.d(tag = TAG) { "Initializing ${task.id} model ${model.name} at $modelPath" }

    engine.initialize(config) { error ->
      if (error.isEmpty()) {
        model.instance = engine
      }
      onDone(error)
    }
  }

  override fun cleanUpModelFn(
    context: PlatformContext,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    if (model.instance != null) {
      try {
        engine.close()
      } catch (e: Exception) {
        Napier.e(tag = TAG) { "Error closing engine for ${model.name}: ${e.message}" }
      }
      model.instance = null
    }
    onDone()
  }

  @Composable
  override fun MainScreen(data: Any) {
    // On iOS, screens are rendered by IosScreenProvider, not through CustomTask.MainScreen().
    Text("This screen is handled by the iOS screen provider.")
  }
}

/**
 * iOS implementation of the AI Chat task.
 */
class IosLlmChatTask(
  override val task: Task,
  engine: LlmInferenceEngine,
) : IosLlmTask(engine)

/**
 * iOS implementation of the Prompt Lab task.
 */
class IosLlmPromptLabTask(
  override val task: Task,
  engine: LlmInferenceEngine,
) : IosLlmTask(engine)
