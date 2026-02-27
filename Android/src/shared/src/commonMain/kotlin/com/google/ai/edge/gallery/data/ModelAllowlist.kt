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

import com.google.ai.edge.gallery.platform.AppPlatform
import com.google.ai.edge.gallery.platform.currentPlatform
import com.google.ai.edge.gallery.platform.isDeviceModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DefaultConfig(
  @SerialName("topK") val topK: Int? = null,
  @SerialName("topP") val topP: Float? = null,
  @SerialName("temperature") val temperature: Float? = null,
  @SerialName("accelerators") val accelerators: String? = null,
  @SerialName("maxTokens") val maxTokens: Int? = null,
)

/** A model in the model allowlist. */
@Serializable
data class AllowedModel(
  val name: String,
  val modelId: String,
  val modelFile: String,
  val description: String,
  val sizeInBytes: Long,
  val commitHash: String,
  val defaultConfig: DefaultConfig,
  val taskTypes: List<String>,
  val disabled: Boolean? = null,
  val llmSupportImage: Boolean? = null,
  val llmSupportAudio: Boolean? = null,
  val llmSupportTinyGarden: Boolean? = null,
  val llmSupportMobileActions: Boolean? = null,
  val minDeviceMemoryInGb: Int? = null,
  val bestForTaskTypes: List<String>? = null,
  val localModelFilePathOverride: String? = null,
  val url: String? = null,
) {
  fun toModel(): Model {
    // Construct HF download url.
    val downloadUrl =
      url ?: "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"

    // On iOS, filter out task types that are not yet supported.
    val effectiveTaskTypes = if (currentPlatform() == AppPlatform.IOS) {
      taskTypes.filter { it !in IOS_UNSUPPORTED_TASK_TYPES }
    } else {
      taskTypes
    }

    // Config.
    val isLlmModel =
      effectiveTaskTypes.contains(BuiltInTaskId.LLM_CHAT) ||
        effectiveTaskTypes.contains(BuiltInTaskId.LLM_PROMPT_LAB) ||
        effectiveTaskTypes.contains(BuiltInTaskId.LLM_ASK_AUDIO) ||
        effectiveTaskTypes.contains(BuiltInTaskId.LLM_ASK_IMAGE) ||
        effectiveTaskTypes.contains(BuiltInTaskId.LLM_MOBILE_ACTIONS) ||
        effectiveTaskTypes.contains(BuiltInTaskId.LLM_TINY_GARDEN)
    var configs: MutableList<Config> = mutableListOf()
    var llmMaxToken = 1024
    var accelerators: List<Accelerator> = DEFAULT_ACCELERATORS
    if (isLlmModel) {
      val defaultTopK: Int = defaultConfig.topK ?: DEFAULT_TOPK
      val defaultTopP: Float = defaultConfig.topP ?: DEFAULT_TOPP
      val defaultTemperature: Float = defaultConfig.temperature ?: DEFAULT_TEMPERATURE
      llmMaxToken = defaultConfig.maxTokens ?: 1024
      if (defaultConfig.accelerators != null) {
        val items = defaultConfig.accelerators.split(",")
        accelerators = mutableListOf()
        for (item in items) {
          if (item == "cpu") {
            accelerators.add(Accelerator.CPU)
          } else if (item == "gpu") {
            accelerators.add(Accelerator.GPU)
          }
        }
        // Remove GPU from Pixel 10 devices (known compatibility issue).
        if (isDeviceModel("pixel 10")) {
          accelerators.remove(Accelerator.GPU)
        }
      }
      configs =
        createLlmChatConfigs(
            defaultTopK = defaultTopK,
            defaultTopP = defaultTopP,
            defaultTemperature = defaultTemperature,
            defaultMaxToken = llmMaxToken,
            accelerators = accelerators,
          )
          .toMutableList()
    }

    // Misc.
    var showBenchmarkButton = true
    var showRunAgainButton = true
    if (isLlmModel) {
      showBenchmarkButton = false
      showRunAgainButton = false
    }

    // On iOS, disable capabilities that require unimplemented platform features.
    val effectiveSupportsImage = llmSupportImage == true &&
      currentPlatform() != AppPlatform.IOS
    val effectiveSupportsAudio = llmSupportAudio == true &&
      currentPlatform() != AppPlatform.IOS
    val effectiveSupportsTinyGarden = llmSupportTinyGarden == true &&
      currentPlatform() != AppPlatform.IOS
    val effectiveSupportsMobileActions = llmSupportMobileActions == true &&
      currentPlatform() != AppPlatform.IOS

    return Model(
      name = name,
      version = commitHash,
      info = description,
      url = downloadUrl,
      sizeInBytes = sizeInBytes,
      minDeviceMemoryInGb = minDeviceMemoryInGb,
      configs = configs,
      downloadFileName = modelFile,
      showBenchmarkButton = showBenchmarkButton,
      showRunAgainButton = showRunAgainButton,
      learnMoreUrl = "https://huggingface.co/${modelId}",
      llmSupportImage = effectiveSupportsImage,
      llmSupportAudio = effectiveSupportsAudio,
      llmSupportTinyGarden = effectiveSupportsTinyGarden,
      llmSupportMobileActions = effectiveSupportsMobileActions,
      llmMaxToken = llmMaxToken,
      accelerators = accelerators,
      bestForTaskIds = (bestForTaskTypes ?: listOf()).let { ids ->
        if (currentPlatform() == AppPlatform.IOS) {
          ids.filter { it !in IOS_UNSUPPORTED_TASK_TYPES }
        } else {
          ids
        }
      },
      localModelFilePathOverride = localModelFilePathOverride ?: "",
      isLlm = isLlmModel,
    )
  }

  override fun toString(): String {
    return "$modelId/$modelFile"
  }
}

/** Task types not yet supported on iOS (require unimplemented platform features). */
private val IOS_UNSUPPORTED_TASK_TYPES = setOf(
  BuiltInTaskId.LLM_ASK_AUDIO,      // Needs AVAudioEngine implementation
  BuiltInTaskId.LLM_ASK_IMAGE,      // Needs AVFoundation camera implementation
  BuiltInTaskId.LLM_TINY_GARDEN,    // Needs WKWebView integration
  BuiltInTaskId.LLM_MOBILE_ACTIONS, // Needs platform-specific function calling
)

/** The model allowlist. */
@Serializable
data class ModelAllowlist(val models: List<AllowedModel>)
