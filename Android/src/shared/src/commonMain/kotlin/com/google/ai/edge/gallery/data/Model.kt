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

package com.google.ai.edge.gallery.data

data class ModelDataFile(
  val name: String,
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

const val IMPORTS_DIR = "__imports"
private val NORMALIZE_NAME_REGEX = Regex("[^a-zA-Z0-9]")

/** Platform-independent file separator. */
const val FILE_SEPARATOR = "/"

data class PromptTemplate(val title: String, val description: String, val prompt: String)

/**
 * A model for a task (see [Task]).
 *
 * A task can have multiple models. For example, a task might be "LLM Chat", and it might have
 * models such as Gemma2, Gemma3, etc.
 */
data class Model(
  val name: String,
  val displayName: String = "",
  val info: String = "",
  var configs: List<Config> = listOf(),
  val learnMoreUrl: String = "",
  val bestForTaskIds: List<String> = listOf(),
  val minDeviceMemoryInGb: Int? = null,
  val url: String = "",
  val sizeInBytes: Long = 0L,
  val downloadFileName: String = "_",
  val version: String = "_",
  val extraDataFiles: List<ModelDataFile> = listOf(),
  val isLlm: Boolean = false,
  val localFileRelativeDirPathOverride: String = "",
  val localModelFilePathOverride: String = "",
  val showRunAgainButton: Boolean = true,
  val showBenchmarkButton: Boolean = true,
  val isZip: Boolean = false,
  val unzipDir: String = "",
  val llmPromptTemplates: List<PromptTemplate> = listOf(),
  val llmSupportImage: Boolean = false,
  val llmSupportAudio: Boolean = false,
  val llmSupportTinyGarden: Boolean = false,
  val llmSupportMobileActions: Boolean = false,
  val llmMaxToken: Int = 0,
  val accelerators: List<Accelerator> = listOf(),
  val imported: Boolean = false,

  // Managed by the app at runtime.
  var normalizedName: String = "",
  var instance: Any? = null,
  var initializing: Boolean = false,
  var cleanUpAfterInit: Boolean = false,
  var configValues: Map<String, Any> = mapOf(),
  var prevConfigValues: Map<String, Any> = mapOf(),
  var totalBytes: Long = 0L,
  var accessToken: String? = null,
) {
  init {
    normalizedName = NORMALIZE_NAME_REGEX.replace(name, "_")
  }

  fun preProcess() {
    val configValues: MutableMap<String, Any> = mutableMapOf()
    for (config in this.configs) {
      configValues[config.key.label] = config.defaultValue
    }
    this.configValues = configValues
    this.totalBytes = this.sizeInBytes + this.extraDataFiles.sumOf { it.sizeInBytes }
  }

  /**
   * Returns the path for this model's file.
   *
   * @param basePath The app's external files directory (platform-specific).
   * @param fileName The specific file to get the path for.
   */
  fun getPath(basePath: String, fileName: String = downloadFileName): String {
    if (imported) {
      return listOf(basePath, fileName).joinToString(FILE_SEPARATOR)
    }

    if (localModelFilePathOverride.isNotEmpty()) {
      return localModelFilePathOverride
    }

    if (localFileRelativeDirPathOverride.isNotEmpty()) {
      return listOf(basePath, localFileRelativeDirPathOverride, fileName)
        .joinToString(FILE_SEPARATOR)
    }

    val baseDir = listOf(basePath, normalizedName, version).joinToString(FILE_SEPARATOR)
    return if (this.isZip && this.unzipDir.isNotEmpty()) {
      listOf(baseDir, this.unzipDir).joinToString(FILE_SEPARATOR)
    } else {
      listOf(baseDir, fileName).joinToString(FILE_SEPARATOR)
    }
  }

  fun getIntConfigValue(key: ConfigKey, defaultValue: Int = 0): Int {
    return getTypedConfigValue(key = key, valueType = ValueType.INT, defaultValue = defaultValue)
      as Int
  }

  fun getFloatConfigValue(key: ConfigKey, defaultValue: Float = 0.0f): Float {
    return getTypedConfigValue(key = key, valueType = ValueType.FLOAT, defaultValue = defaultValue)
      as Float
  }

  fun getBooleanConfigValue(key: ConfigKey, defaultValue: Boolean = false): Boolean {
    return getTypedConfigValue(
      key = key,
      valueType = ValueType.BOOLEAN,
      defaultValue = defaultValue,
    )
      as Boolean
  }

  fun getStringConfigValue(key: ConfigKey, defaultValue: String = ""): String {
    return getTypedConfigValue(key = key, valueType = ValueType.STRING, defaultValue = defaultValue)
      as String
  }

  fun getExtraDataFile(name: String): ModelDataFile? {
    return extraDataFiles.find { it.name == name }
  }

  private fun getTypedConfigValue(key: ConfigKey, valueType: ValueType, defaultValue: Any): Any {
    return convertValueToTargetType(
      value = configValues[key.label] ?: defaultValue,
      valueType = valueType,
    )
  }
}

enum class ModelDownloadStatusType {
  NOT_DOWNLOADED,
  PARTIALLY_DOWNLOADED,
  IN_PROGRESS,
  UNZIPPING,
  SUCCEEDED,
  FAILED,
}

data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
  val remainingMs: Long = 0,
)

val EMPTY_MODEL: Model =
  Model(name = "empty", downloadFileName = "empty.tflite", url = "", sizeInBytes = 0L)
