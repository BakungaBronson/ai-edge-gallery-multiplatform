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

import kotlinx.serialization.Serializable

/** Theme setting, replacing proto Theme enum. */
@Serializable
enum class AppTheme {
  THEME_UNSPECIFIED,
  THEME_LIGHT,
  THEME_DARK,
  THEME_AUTO,
}

/** Access token data for HuggingFace auth, replacing proto AccessTokenData. */
@Serializable
data class AppAccessTokenData(
  val accessToken: String = "",
  val refreshToken: String = "",
  val expiresAtMs: Long = 0L,
)

/** LLM configuration for imported models, replacing proto LlmConfig. */
@Serializable
data class AppLlmConfig(
  val compatibleAccelerators: List<String> = listOf(),
  val defaultMaxTokens: Int = 0,
  val defaultTopk: Int = 0,
  val defaultTopp: Float = 0f,
  val defaultTemperature: Float = 0f,
  val supportImage: Boolean = false,
  val supportAudio: Boolean = false,
  val supportTinyGarden: Boolean = false,
  val supportMobileActions: Boolean = false,
)

/** Imported model configuration, replacing proto ImportedModel. */
@Serializable
data class AppImportedModel(
  val fileName: String = "",
  val fileSize: Long = 0L,
  val llmConfig: AppLlmConfig? = null,
)

/** App settings, replacing proto Settings message. */
@Serializable
data class AppSettings(
  val theme: AppTheme = AppTheme.THEME_UNSPECIFIED,
  val textInputHistory: List<String> = listOf(),
  val importedModels: List<AppImportedModel> = listOf(),
  val isTosAccepted: Boolean = false,
  val hasRunTinyGarden: Boolean = false,
  val hasSeenBenchmarkComparisonHelp: Boolean = false,
  val isGemmaTermsAccepted: Boolean = false,
)

/** User data (tokens), replacing proto UserData message. */
@Serializable
data class AppUserData(
  val accessTokenData: AppAccessTokenData? = null,
)

/** A 2D point, replacing proto Point. */
@Serializable
data class AppPoint(val x: Float = 0f, val y: Float = 0f)

/** Fill mode for cutouts, replacing proto FillMode. */
@Serializable
enum class AppFillMode {
  FILL_MODE_UNSPECIFIED,
  FILL_MODE_DISABLED,
  FILL_MODE_SOLID,
  FILL_MODE_COLORIZE,
}

/** Stroke path for cutout doodles, replacing proto StrokePath. */
@Serializable
data class AppStrokePath(
  val points: List<AppPoint> = listOf(),
  val brushColor: Int = 0,
  val brushSize: Float = 0f,
  val brushSoftness: Float = 0f,
  val blurType: Int = 0,
)

/** A cutout entry, replacing proto Cutout. */
@Serializable
data class AppCutout(
  val id: String = "",
  val rotationDegree: Int = 0,
  val borderWidth: Int = 0,
  val borderColor: Int = 0,
  val fillColor: Int = 0,
  val fillMode: AppFillMode = AppFillMode.FILL_MODE_UNSPECIFIED,
  val doodleStrokes: List<AppStrokePath> = listOf(),
)

/** Collection of cutouts, replacing proto CutoutCollection. */
@Serializable
data class AppCutoutCollection(
  val cutouts: List<AppCutout> = listOf(),
)

/** Value series for benchmark statistics, replacing proto ValueSeries. */
@Serializable
data class AppValueSeries(
  val values: List<Double> = listOf(),
  val min: Double = 0.0,
  val max: Double = 0.0,
  val avg: Double = 0.0,
  val median: Double = 0.0,
  val pct25: Double = 0.0,
  val pct75: Double = 0.0,
)

/** LLM benchmark basic info, replacing proto LlmBenchmarkBasicInfo. */
@Serializable
data class AppLlmBenchmarkBasicInfo(
  val startMs: Long = 0L,
  val endMs: Long = 0L,
  val modelName: String = "",
  val accelerator: String = "",
  val prefillTokens: Int = 0,
  val decodeTokens: Int = 0,
  val numberOfRuns: Int = 0,
  val appVersion: String = "",
)

/** LLM benchmark stats, replacing proto LlmBenchmarkStats. */
@Serializable
data class AppLlmBenchmarkStats(
  val prefillSpeed: AppValueSeries = AppValueSeries(),
  val decodeSpeed: AppValueSeries = AppValueSeries(),
  val timeToFirstToken: AppValueSeries = AppValueSeries(),
  val firstInitTimeMs: Double = 0.0,
  val nonFirstInitTimeMs: AppValueSeries = AppValueSeries(),
)

/** LLM benchmark result, replacing proto LlmBenchmarkResult. */
@Serializable
data class AppLlmBenchmarkResult(
  val basicInfo: AppLlmBenchmarkBasicInfo = AppLlmBenchmarkBasicInfo(),
  val stats: AppLlmBenchmarkStats = AppLlmBenchmarkStats(),
)

/** A benchmark result (union type), replacing proto BenchmarkResult. */
@Serializable
data class AppBenchmarkResult(
  val llmResult: AppLlmBenchmarkResult? = null,
)

/** Collection of benchmark results, replacing proto BenchmarkResults. */
@Serializable
data class AppBenchmarkResults(
  val results: List<AppBenchmarkResult> = listOf(),
)
