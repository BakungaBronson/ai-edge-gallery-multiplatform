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

import com.google.ai.edge.gallery.common.formatFloat
import com.google.ai.edge.gallery.inference.LlmGenerationOptions
import com.google.ai.edge.gallery.llm.CRANE_SYSTEM_PROMPT
import kotlin.math.abs

/**
 * The types of configuration editors available.
 *
 * This enum defines the different UI components used to edit configuration values. Each type
 * corresponds to a specific editor widget, such as a slider or a switch.
 */
enum class ConfigEditorType {
  LABEL,
  NUMBER_SLIDER,
  BOOLEAN_SWITCH,
  SEGMENTED_BUTTON,
  BOTTOMSHEET_SELECTOR,
  TEXT_INPUT,
}

/** The data types of configuration values. */
enum class ValueType {
  INT,
  FLOAT,
  DOUBLE,
  STRING,
  BOOLEAN,
}

data class ConfigKey(val id: String, val label: String)

object ConfigKeys {
  val MAX_TOKENS = ConfigKey("max_tokens", "Max tokens")
  val TOPK = ConfigKey("topk", "TopK")
  val TOPP = ConfigKey("topp", "TopP")
  val TEMPERATURE = ConfigKey("temperature", "Temperature")
  val DEFAULT_MAX_TOKENS = ConfigKey("default_max_tokens", "Default max tokens")
  val DEFAULT_TOPK = ConfigKey("default_topk", "Default TopK")
  val DEFAULT_TOPP = ConfigKey("default_topp", "Default TopP")
  val DEFAULT_TEMPERATURE = ConfigKey("default_temperature", "Default temperature")
  val SUPPORT_IMAGE = ConfigKey("support_image", "Support image")
  val SUPPORT_AUDIO = ConfigKey("support_audio", "Support audio")
  val SUPPORT_TINY_GARDEN = ConfigKey("support_tiny_garden", "Support tiny garden")
  val SUPPORT_MOBILE_ACTIONS = ConfigKey("support_mobile_actions", "Support mobile actions")
  val MAX_RESULT_COUNT = ConfigKey("max_result_count", "Max result count")
  val USE_GPU = ConfigKey("use_gpu", "Use GPU")
  val ACCELERATOR = ConfigKey("accelerator", "Accelerator")
  val COMPATIBLE_ACCELERATORS = ConfigKey("compatible_accelerators", "Compatible accelerators")
  val WARM_UP_ITERATIONS = ConfigKey("warm_up_iterations", "Warm up iterations")
  val BENCHMARK_ITERATIONS = ConfigKey("benchmark_iterations", "Benchmark iterations")
  val ITERATIONS = ConfigKey("iterations", "Iterations")
  val THEME = ConfigKey("theme", "Theme")
  val NAME = ConfigKey("name", "Name")
  val MODEL_TYPE = ConfigKey("model_type", "Model type")
  val MODEL = ConfigKey("model", "Model")
  val RESET_CONVERSATION_TURN_COUNT =
    ConfigKey("reset_conversation_turn_count", "Number of turns before the conversation resets")
  val PREFILL_TOKENS = ConfigKey("prefill_tokens", "Prefill tokens")
  val DECODE_TOKENS = ConfigKey("decode_tokens", "Decode tokens")
  val NUMBER_OF_RUNS = ConfigKey("number_of_runs", "Number of runs")

  // Crane decoding guards + system prompt (applied via the LiteRT-LM C-API). Wired into
  // createLlmChatConfigs below, so these show up as settings-sheet rows (NUMBER_SLIDER x2 +
  // TEXT_INPUT) and their edited values flow through Model.configValues like any other config.
  val REPETITION_PENALTY = ConfigKey("repetition_penalty", "Repetition penalty")
  val NO_REPEAT_NGRAM = ConfigKey("no_repeat_ngram", "No-repeat n-gram size")
  val SYSTEM_PROMPT = ConfigKey("system_prompt", "System prompt")
}

/**
 * Base class for configuration settings.
 *
 * @param type The type of configuration editor.
 * @param key The unique key for the configuration setting.
 * @param defaultValue The default value for the configuration setting.
 * @param valueType The data type of the configuration value.
 * @param needReinitialization Indicates whether the model needs to be reinitialized after changing
 *   this config.
 */
open class Config(
  val type: ConfigEditorType,
  open val key: ConfigKey,
  open val defaultValue: Any,
  open val valueType: ValueType,
  open val needReinitialization: Boolean = true,
)

/** Configuration setting for a label. */
class LabelConfig(override val key: ConfigKey, override val defaultValue: String = "") :
  Config(
    type = ConfigEditorType.LABEL,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

/**
 * Configuration setting for a number slider.
 *
 * @param sliderMin The minimum value of the slider.
 * @param sliderMax The maximum value of the slider.
 */
class NumberSliderConfig(
  override val key: ConfigKey,
  val sliderMin: Float,
  val sliderMax: Float,
  override val defaultValue: Float,
  override val valueType: ValueType,
  override val needReinitialization: Boolean = true,
) :
  Config(
    type = ConfigEditorType.NUMBER_SLIDER,
    key = key,
    defaultValue = defaultValue,
    valueType = valueType,
  )

/** Configuration setting for a boolean switch. */
class BooleanSwitchConfig(
  override val key: ConfigKey,
  override val defaultValue: Boolean,
  override val needReinitialization: Boolean = true,
) :
  Config(
    type = ConfigEditorType.BOOLEAN_SWITCH,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.BOOLEAN,
  )

/** Configuration setting for a segmented button. */
class SegmentedButtonConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val options: List<String>,
  val allowMultiple: Boolean = false,
) :
  Config(
    type = ConfigEditorType.SEGMENTED_BUTTON,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

/** Configuration setting for a bottom sheet selector. */
class BottomSheetSelectorConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val options: List<BottomSheetSelectorItem>,
  /** String key for the bottom sheet title (replaces Android @StringRes). */
  val bottomSheetTitleKey: String? = null,
) :
  Config(
    type = ConfigEditorType.BOTTOMSHEET_SELECTOR,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

data class BottomSheetSelectorItem(val label: String)

/**
 * Configuration setting for a free-text (potentially multiline) input, e.g. the Crane system
 * prompt.
 */
class TextInputConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val multiline: Boolean = true,
  override val needReinitialization: Boolean = true,
) :
  Config(
    type = ConfigEditorType.TEXT_INPUT,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

fun convertValueToTargetType(value: Any, valueType: ValueType): Any {
  return when (valueType) {
    ValueType.INT ->
      when (value) {
        is Int -> value
        is Float -> value.toInt()
        is Double -> value.toInt()
        is Long -> value.toInt()
        is Number -> value.toLong().toInt()
        is String -> value.toIntOrNull() ?: ""
        is Boolean -> if (value) 1 else 0
        else -> ""
      }

    ValueType.FLOAT ->
      when (value) {
        is Int -> value.toFloat()
        is Float -> value
        is Double -> value.toFloat()
        is Long -> value.toFloat()
        is Number -> value.toDouble().toFloat()
        is String -> value.toFloatOrNull() ?: ""
        is Boolean -> if (value) 1f else 0f
        else -> ""
      }

    ValueType.DOUBLE ->
      when (value) {
        is Int -> value.toDouble()
        is Float -> value.toDouble()
        is Double -> value
        is Long -> value.toDouble()
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: ""
        is Boolean -> if (value) 1.0 else 0.0
        else -> ""
      }

    ValueType.BOOLEAN ->
      when (value) {
        is Int -> value == 0
        is Boolean -> value
        is Float -> abs(value) > 1e-6
        is Double -> abs(value) > 1e-6
        is Long -> value == 0L
        is Number -> value.toDouble() == 0.0
        is String -> value.isNotEmpty()
        else -> false
      }

    ValueType.STRING -> value.toString()
  }
}

fun createLlmChatConfigs(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  defaultTopK: Int = DEFAULT_TOPK,
  defaultTopP: Float = DEFAULT_TOPP,
  defaultTemperature: Float = DEFAULT_TEMPERATURE,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
): List<Config> {
  return listOf(
    LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken"),
    NumberSliderConfig(
      key = ConfigKeys.TOPK,
      sliderMin = 5f,
      sliderMax = 100f,
      defaultValue = defaultTopK.toFloat(),
      valueType = ValueType.INT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.TOPP,
      sliderMin = 0.0f,
      sliderMax = 1.0f,
      defaultValue = defaultTopP,
      valueType = ValueType.FLOAT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.TEMPERATURE,
      sliderMin = 0.0f,
      sliderMax = 2.0f,
      defaultValue = defaultTemperature,
      valueType = ValueType.FLOAT,
    ),
    // Crane decoding guards: applied per-send via the LiteRT-LM C-API, so no reinitialization
    // needed when these change (matches the crane-wrapper reference's proven serving values).
    NumberSliderConfig(
      key = ConfigKeys.REPETITION_PENALTY,
      sliderMin = 1.0f,
      sliderMax = 1.5f,
      defaultValue = LlmGenerationOptions.DEFAULT_REPETITION_PENALTY,
      valueType = ValueType.FLOAT,
      needReinitialization = false,
    ),
    NumberSliderConfig(
      key = ConfigKeys.NO_REPEAT_NGRAM,
      sliderMin = 0f,
      sliderMax = 6f,
      defaultValue = LlmGenerationOptions.DEFAULT_NO_REPEAT_NGRAM_SIZE.toFloat(),
      valueType = ValueType.INT,
      needReinitialization = false,
    ),
    // System prompt is applied at conversation creation, so editing it does reinitialize.
    TextInputConfig(key = ConfigKeys.SYSTEM_PROMPT, defaultValue = CRANE_SYSTEM_PROMPT),
    SegmentedButtonConfig(
      key = ConfigKeys.ACCELERATOR,
      defaultValue = accelerators[0].label,
      options = accelerators.map { it.label },
    ),
  )
}

fun getConfigValueString(value: Any, config: Config): String {
  var strNewValue = "$value"
  if (config.valueType == ValueType.FLOAT) {
    strNewValue = formatFloat((value as? Number)?.toFloat() ?: 0f)
  }
  return strNewValue
}
