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

package com.google.ai.edge.gallery.ui.benchmark

import com.google.ai.edge.gallery.data.AppBenchmarkResult
import kotlinx.coroutines.flow.StateFlow

enum class Aggregation(val label: String) {
  AVG(label = "avg"),
  MEDIAN(label = "median"),
  MIN(label = "min"),
  MAX(label = "max"),
}

data class BenchmarkResultInfo(
  val id: String,
  val benchmarkResult: AppBenchmarkResult,
  val expanded: Boolean = false,
  val basicInfoExpanded: Boolean = true,
  val statsExpanded: Boolean = true,
  val aggregation: Aggregation = Aggregation.AVG,
)

data class BenchmarkUiState(
  val results: List<BenchmarkResultInfo> = listOf(),
  val baselineResult: BenchmarkResultInfo? = null,
  val showResultsViewer: Boolean = false,
  val running: Boolean = false,
  val totalRunCount: Int = 0,
  val completedRunCount: Int = 0,
)

/**
 * Interface abstracting the benchmark operations needed by shared UI composables.
 */
interface BenchmarkActions {
  /** Observable UI state. */
  val uiState: StateFlow<BenchmarkUiState>

  /** Show or hide the results viewer. */
  fun setShowResultsViewer(showResultsViewer: Boolean)

  /** Set a result as the comparison baseline. */
  fun setBaseline(id: String)

  /** Clear the baseline. */
  fun clearBaseline()

  /** Expand a result card. */
  fun setExpanded(id: String, expanded: Boolean)

  /** Expand/collapse the basic info section. */
  fun setBasicInfoExpanded(id: String, expanded: Boolean)

  /** Expand/collapse the stats section. */
  fun setStatsExpanded(id: String, expanded: Boolean)

  /** Expand all result cards. */
  fun expandAll()

  /** Collapse all result cards. */
  fun collapseAll()

  /** Set the aggregation method for a result. */
  fun setAggregation(id: String, aggregation: Aggregation)

  /** Delete a benchmark result. */
  fun deleteBenchmarkResult(id: String)

  /** Check if the user has seen the benchmark comparison help. */
  fun getHasSeenBenchmarkComparisonHelp(): Boolean

  /** Mark that the user has seen the benchmark comparison help. */
  fun setHasSeenBenchmarkComparisonHelp(seen: Boolean)
}
