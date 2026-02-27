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

/** Common interface for managing app data persistence across platforms. */
interface DataStoreRepository {
  fun saveTextInputHistory(history: List<String>)
  fun readTextInputHistory(): List<String>

  fun saveTheme(theme: AppTheme)
  fun readTheme(): AppTheme

  fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long)
  fun clearAccessTokenData()
  fun readAccessTokenData(): AppAccessTokenData?

  fun saveImportedModels(importedModels: List<AppImportedModel>)
  fun readImportedModels(): List<AppImportedModel>

  fun isTosAccepted(): Boolean
  fun acceptTos()

  fun isGemmaTermsOfUseAccepted(): Boolean
  fun acceptGemmaTermsOfUse()

  fun getHasRunTinyGarden(): Boolean
  fun setHasRunTinyGarden(hasRun: Boolean)

  fun addCutout(cutout: AppCutout)
  fun getAllCutouts(): List<AppCutout>
  fun setCutout(newCutout: AppCutout)
  fun setCutouts(cutouts: List<AppCutout>)

  fun setHasSeenBenchmarkComparisonHelp(seen: Boolean)
  fun getHasSeenBenchmarkComparisonHelp(): Boolean

  fun addBenchmarkResult(result: AppBenchmarkResult)
  fun getAllBenchmarkResults(): List<AppBenchmarkResult>
  fun deleteBenchmarkResult(index: Int)
}
