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

package com.google.ai.edge.gallery.ui.modelmanager

import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.AppTheme
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface abstracting the core model management operations needed by shared UI composables.
 *
 * Platform-specific implementations (e.g., Android's ModelManagerViewModel) implement this
 * interface, allowing shared Compose UI code to interact with model management without depending
 * on platform-specific ViewModel classes.
 */
interface ModelManagerActions {
  /** Observable UI state containing tasks, download statuses, initialization statuses, etc. */
  val uiState: StateFlow<ModelManagerUiState>

  /** Select a model as the currently active model. */
  fun selectModel(model: Model)

  /** Delete a downloaded model's files and update status. */
  fun deleteModel(model: Model)

  /** Start downloading a model for the given task. */
  fun downloadModel(task: Task?, model: Model)

  /** Cancel an in-progress model download. */
  fun cancelDownloadModel(model: Model)

  /**
   * Initialize a model for inference.
   *
   * Platform implementations should use their own context internally.
   */
  fun initializeModel(task: Task, model: Model, force: Boolean = false)

  /**
   * Clean up a model's resources (e.g., release inference engine).
   *
   * Platform implementations should use their own context internally.
   */
  fun cleanupModel(task: Task, model: Model, onDone: () -> Unit = {})

  /** Trigger a UI update for config value changes. */
  fun updateConfigValuesUpdateTrigger()

  /** Find a task by its ID. */
  fun getTaskById(id: String): Task?

  /** Find a model by its name across all tasks. */
  fun getModelByName(name: String): Model?

  /** Get all successfully downloaded LLM models. */
  fun getAllDownloadedModels(): List<Model>

  /** Add a text input to the history. */
  fun addTextInputHistory(text: String)

  /** Load the model allowlist from network/disk. */
  fun loadModelAllowlist()

  /** Clear any error from loading the model allowlist. */
  fun clearLoadModelAllowlistError()

  /** Find a custom task by its task ID. */
  fun getCustomTaskByTaskId(id: String): CustomTask?

  /** Get all models across all tasks. */
  fun getAllModels(): List<Model>

  /** Notify the model manager whether the app is in the foreground. */
  fun setAppInForeground(foreground: Boolean)

  /** Read the current theme override setting. */
  fun readThemeOverride(): AppTheme

  /** Save the theme override setting. */
  fun saveThemeOverride(theme: AppTheme)
}
