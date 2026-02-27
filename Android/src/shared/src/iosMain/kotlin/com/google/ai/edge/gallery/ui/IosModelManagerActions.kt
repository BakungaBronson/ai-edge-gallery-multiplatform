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

package com.google.ai.edge.gallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Science
import com.google.ai.edge.gallery.customtasks.IosLlmChatTask
import com.google.ai.edge.gallery.customtasks.IosLlmPromptLabTask
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.AppTheme
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelAllowlist
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.inference.LlmBackend
import com.google.ai.edge.gallery.inference.LlmEngineConfig
import com.google.ai.edge.gallery.inference.LlmInferenceEngine
import com.google.ai.edge.gallery.network.getJsonResponse
import com.google.ai.edge.gallery.platform.PlatformContext
import com.google.ai.edge.gallery.platform.getAppFilesDirectory
import com.google.ai.edge.gallery.platform.getCacheDirectory
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatus
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerUiState
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager

private const val TAG = "IosModelManagerActions"

private const val ALLOWLIST_BASE_URL =
  "https://raw.githubusercontent.com/google-ai-edge/gallery/refs/heads/main/model_allowlists"

// Use the same version as the Android app for allowlist compatibility.
private const val ALLOWLIST_VERSION = "1_0_10"

/**
 * iOS implementation of [ModelManagerActions].
 *
 * Provides the model management operations needed by shared UI composables
 * using iOS-specific backends (IosDownloadRepository, etc.).
 */
@OptIn(ExperimentalForeignApi::class)
class IosModelManagerActions(
  private val dataStoreRepository: DataStoreRepository,
  private val downloadRepository: DownloadRepository,
  private val llmInferenceEngine: LlmInferenceEngine,
) : ModelManagerActions {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val fileManager = NSFileManager.defaultManager
  private val platformContext = PlatformContext()

  /** iOS custom task registry, populated after allowlist loads. */
  private val customTasks = mutableMapOf<String, CustomTask>()

  private val _uiState = MutableStateFlow(
    ModelManagerUiState(
      tasks = emptyList(),
      tasksByCategory = emptyMap(),
      modelDownloadStatus = emptyMap(),
      modelInitializationStatus = emptyMap(),
    )
  )

  override val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

  /** Create the base iOS task definitions (no models yet). */
  private fun createIosTasks(): List<Task> {
    return listOf(
      Task(
        id = BuiltInTaskId.LLM_CHAT,
        label = "AI Chat",
        category = Category.LLM,
        icon = Icons.Outlined.Forum,
        models = mutableListOf(),
        description = "Chat with on-device large language models",
      ),
      Task(
        id = BuiltInTaskId.LLM_PROMPT_LAB,
        label = "Prompt Lab",
        category = Category.LLM,
        icon = Icons.Outlined.Science,
        models = mutableListOf(),
        description = "Experiment with prompts for on-device LLMs",
      ),
    )
  }

  override fun selectModel(model: Model) {
    _uiState.update { it.copy(selectedModel = model) }
  }

  override fun deleteModel(model: Model) {
    scope.launch(Dispatchers.IO) {
      try {
        // Close the engine if this model is currently initialized.
        val initStatus = _uiState.value.modelInitializationStatus[model.name]
        if (initStatus?.status == ModelInitializationStatusType.INITIALIZED) {
          llmInferenceEngine.close()
          model.instance = null
        }

        // Delete model files from disk.
        val basePath = platformContext.getAppFilesDirectory()
        val modelPath = model.getPath(basePath = basePath)
        val modelDir = modelPath.substringBeforeLast("/")

        if (fileManager.fileExistsAtPath(modelDir)) {
          fileManager.removeItemAtPath(modelDir, error = null)
          Napier.d(tag = TAG) { "Deleted model directory: $modelDir" }
        }

        // Update UI state.
        _uiState.update { state ->
          val newDownloadStatus = state.modelDownloadStatus.toMutableMap()
          newDownloadStatus[model.name] = ModelDownloadStatus(
            status = ModelDownloadStatusType.NOT_DOWNLOADED,
          )
          val newInitStatus = state.modelInitializationStatus.toMutableMap()
          newInitStatus.remove(model.name)
          state.copy(
            modelDownloadStatus = newDownloadStatus,
            modelInitializationStatus = newInitStatus,
          )
        }
      } catch (e: Exception) {
        Napier.e(tag = TAG) { "Error deleting model ${model.name}: ${e.message}" }
      }
    }
  }

  override fun downloadModel(task: Task?, model: Model) {
    downloadRepository.downloadModel(task, model) { _, status ->
      _uiState.update { state ->
        val newStatus = state.modelDownloadStatus.toMutableMap()
        newStatus[model.name] = status
        state.copy(modelDownloadStatus = newStatus)
      }
    }
  }

  override fun cancelDownloadModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    _uiState.update { state ->
      val newStatus = state.modelDownloadStatus.toMutableMap()
      newStatus[model.name] = ModelDownloadStatus(
        status = ModelDownloadStatusType.NOT_DOWNLOADED,
      )
      state.copy(modelDownloadStatus = newStatus)
    }
  }

  override fun initializeModel(task: Task, model: Model, force: Boolean) {
    scope.launch(Dispatchers.Default) {
      // Skip if already initialized (unless force=true).
      val currentStatus = _uiState.value.modelInitializationStatus[model.name]
      if (!force && currentStatus?.status == ModelInitializationStatusType.INITIALIZED) {
        return@launch
      }

      // Skip if initialization is already in progress.
      if (model.initializing) {
        model.cleanUpAfterInit = false
        return@launch
      }

      // Clean up any previous instance.
      cleanupModel(task = task, model = model)

      // Mark as initializing.
      model.initializing = true
      updateModelInitializationStatus(model, ModelInitializationStatusType.INITIALIZING)

      // Extract config values from model.
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

      // Get model file path.
      val basePath = platformContext.getAppFilesDirectory()
      val modelPath = model.getPath(basePath = basePath)
      val cacheDir = platformContext.getCacheDirectory()

      val config = LlmEngineConfig(
        modelPath = modelPath,
        backend = backend,
        maxNumTokens = maxTokens,
        cacheDir = cacheDir,
      )

      Napier.d(tag = TAG) { "Initializing model ${model.name} at $modelPath with backend=$backend" }

      llmInferenceEngine.initialize(config) { error ->
        model.initializing = false

        if (error.isEmpty()) {
          // Success — store engine reference in model.instance.
          model.instance = llmInferenceEngine
          updateModelInitializationStatus(model, ModelInitializationStatusType.INITIALIZED)
          Napier.d(tag = TAG) { "Model ${model.name} initialized successfully" }

          if (model.cleanUpAfterInit) {
            cleanupModel(task = task, model = model)
          }
        } else {
          // Failure.
          model.instance = null
          updateModelInitializationStatus(model, ModelInitializationStatusType.ERROR, error)
          Napier.e(tag = TAG) { "Model ${model.name} initialization failed: $error" }
        }
      }
    }
  }

  private fun updateModelInitializationStatus(
    model: Model,
    status: ModelInitializationStatusType,
    error: String = "",
  ) {
    _uiState.update { state ->
      val newStatus = state.modelInitializationStatus.toMutableMap()
      newStatus[model.name] = ModelInitializationStatus(status = status, error = error)
      state.copy(modelInitializationStatus = newStatus)
    }
  }

  override fun cleanupModel(task: Task, model: Model, onDone: () -> Unit) {
    if (model.instance != null) {
      try {
        llmInferenceEngine.close()
      } catch (e: Exception) {
        Napier.e(tag = TAG) { "Error closing engine for ${model.name}: ${e.message}" }
      }
      model.instance = null
      updateModelInitializationStatus(model, ModelInitializationStatusType.NOT_INITIALIZED)
    }
    onDone()
  }

  override fun updateConfigValuesUpdateTrigger() {
    _uiState.update { it.copy(configValuesUpdateTrigger = it.configValuesUpdateTrigger + 1) }
  }

  override fun getTaskById(id: String): Task? {
    return _uiState.value.tasks.find { it.id == id }
  }

  override fun getModelByName(name: String): Model? {
    return _uiState.value.tasks.flatMap { it.models }.find { it.name == name }
  }

  override fun getAllDownloadedModels(): List<Model> {
    return _uiState.value.tasks
      .flatMap { it.models }
      .filter { _uiState.value.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED }
  }

  override fun addTextInputHistory(text: String) {
    _uiState.update {
      val history = it.textInputHistory.toMutableList()
      history.remove(text)
      history.add(0, text)
      it.copy(textInputHistory = history)
    }
  }

  override fun loadModelAllowlist() {
    scope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(loadingModelAllowlist = true, loadingModelAllowlistError = "") }
      try {
        val url = "$ALLOWLIST_BASE_URL/${ALLOWLIST_VERSION}.json"
        Napier.d(tag = TAG) { "Loading model allowlist from: $url" }
        val data = getJsonResponse<ModelAllowlist>(url = url)
        val modelAllowlist = data?.jsonObj

        if (modelAllowlist == null) {
          _uiState.update {
            it.copy(
              loadingModelAllowlist = false,
              loadingModelAllowlistError = "Failed to load model list",
            )
          }
          return@launch
        }

        // Create base tasks and fill with models from allowlist.
        val tasks = createIosTasks()
        val nameToModel = mutableMapOf<String, Model>()
        for (allowedModel in modelAllowlist.models) {
          if (allowedModel.disabled == true) continue

          val model = allowedModel.toModel()
          nameToModel[model.name] = model
          for (taskType in allowedModel.taskTypes) {
            val task = tasks.find { it.id == taskType }
            task?.models?.add(model)
          }
        }

        // Register iOS custom tasks for each task type.
        customTasks.clear()
        for (task in tasks) {
          val customTask: CustomTask? = when (task.id) {
            BuiltInTaskId.LLM_CHAT -> IosLlmChatTask(task, llmInferenceEngine)
            BuiltInTaskId.LLM_PROMPT_LAB -> IosLlmPromptLabTask(task, llmInferenceEngine)
            else -> null
          }
          if (customTask != null) {
            customTasks[task.id] = customTask
          }
        }

        // Remove tasks with no models.
        val activeTasks = tasks.filter { it.models.isNotEmpty() }
        val tasksByCategory = activeTasks.groupBy { it.category.id }

        // Initialize download status — check for already-downloaded models.
        val basePath = platformContext.getAppFilesDirectory()
        val downloadStatus = mutableMapOf<String, ModelDownloadStatus>()
        for (task in activeTasks) {
          for (model in task.models) {
            model.preProcess()
            val modelPath = model.getPath(basePath = basePath)
            val isDownloaded = fileManager.fileExistsAtPath(modelPath)
            downloadStatus[model.name] = if (isDownloaded) {
              ModelDownloadStatus(
                status = ModelDownloadStatusType.SUCCEEDED,
                totalBytes = model.totalBytes,
                receivedBytes = model.totalBytes,
              )
            } else {
              ModelDownloadStatus(
                status = ModelDownloadStatusType.NOT_DOWNLOADED,
              )
            }
          }
        }

        _uiState.update {
          it.copy(
            tasks = activeTasks,
            tasksByCategory = tasksByCategory,
            modelDownloadStatus = downloadStatus,
            loadingModelAllowlist = false,
          )
        }
      } catch (e: Exception) {
        Napier.e(tag = TAG) { "Error loading allowlist: ${e.message}" }
        _uiState.update {
          it.copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = e.message ?: "Failed to load model list",
          )
        }
      }
    }
  }

  override fun clearLoadModelAllowlistError() {
    _uiState.update { it.copy(loadingModelAllowlistError = "") }
  }

  override fun getCustomTaskByTaskId(id: String): CustomTask? {
    return customTasks[id]
  }

  override fun getAllModels(): List<Model> {
    return _uiState.value.tasks.flatMap { it.models }
  }

  override fun setAppInForeground(foreground: Boolean) {
    // iOS lifecycle handling
  }

  override fun readThemeOverride(): AppTheme {
    return dataStoreRepository.readTheme()
  }

  override fun saveThemeOverride(theme: AppTheme) {
    dataStoreRepository.saveTheme(theme)
  }
}
