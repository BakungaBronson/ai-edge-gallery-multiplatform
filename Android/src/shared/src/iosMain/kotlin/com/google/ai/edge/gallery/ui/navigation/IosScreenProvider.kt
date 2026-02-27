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

package com.google.ai.edge.gallery.ui.navigation

import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.platform.logAnalyticsEvent
import com.google.ai.edge.gallery.ui.benchmark.IosBenchmarkScreen
import com.google.ai.edge.gallery.ui.home.IosSettingsDialog
import com.google.ai.edge.gallery.ui.modelmanager.IosGlobalModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import com.google.ai.edge.gallery.ui.modelscreen.IosModelScreen

/**
 * iOS implementation of [GalleryScreenProvider].
 *
 * Delegates to real implementations using shared composables for model list,
 * and iOS-specific implementations for model screen, global model manager,
 * benchmark, and settings.
 */
class IosScreenProvider : GalleryScreenProvider {

  @Composable
  override fun ModelListScreen(
    task: Task,
    modelManagerActions: ModelManagerActions,
    enableAnimation: Boolean,
    onModelClicked: (Model) -> Unit,
    navigateUp: () -> Unit,
  ) {
    ModelManager(
      task = task,
      modelManagerActions = modelManagerActions,
      enableAnimation = enableAnimation,
      onModelClicked = onModelClicked,
      navigateUp = navigateUp,
    )
  }

  @Composable
  override fun ModelScreen(
    taskId: String,
    modelName: String,
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    enableModelListAnimation: () -> Unit,
  ) {
    val task = modelManagerActions.getTaskById(taskId)
    val model = modelManagerActions.getModelByName(modelName)
    if (task != null && model != null) {
      IosModelScreen(
        task = task,
        model = model,
        modelManagerActions = modelManagerActions,
        navigateUp = {
          enableModelListAnimation()
          navigateUp()
        },
      )
    }
  }

  @Composable
  override fun GlobalModelManagerScreen(
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    onModelSelected: (Task, Model) -> Unit,
    onBenchmarkClicked: (Model) -> Unit,
  ) {
    IosGlobalModelManager(
      modelManagerActions = modelManagerActions,
      navigateUp = navigateUp,
      onModelSelected = onModelSelected,
      onBenchmarkClicked = onBenchmarkClicked,
    )
  }

  @Composable
  override fun BenchmarkScreen(
    modelName: String,
    modelManagerActions: ModelManagerActions,
    onBackClicked: () -> Unit,
  ) {
    IosBenchmarkScreen(
      modelName = modelName,
      modelManagerActions = modelManagerActions,
      onBackClicked = onBackClicked,
    )
  }

  @Composable
  override fun SettingsDialog(modelManagerActions: ModelManagerActions, onDismiss: () -> Unit) {
    IosSettingsDialog(
      modelManagerActions = modelManagerActions,
      onDismiss = onDismiss,
    )
  }

  override fun onTaskSelected(task: Task) {
    logAnalyticsEvent(
      "capability_select",
      mapOf("task_id" to task.id, "task_label" to task.label),
    )
  }

  override fun onBenchmarkSelected(model: Model) {
    logAnalyticsEvent(
      "button_clicked",
      mapOf("action" to "benchmark", "model_name" to model.name),
    )
  }
}
