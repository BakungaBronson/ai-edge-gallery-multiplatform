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

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "AGDataStoreRepo"

private val json = Json {
  prettyPrint = false
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/** Repository for managing data using JSON file storage. */
class DefaultDataStoreRepository(private val context: Context) : DataStoreRepository {

  private val settingsFile get() = File(context.filesDir, "settings.json")
  private val userDataFile get() = File(context.filesDir, "user_data.json")
  private val cutoutsFile get() = File(context.filesDir, "cutouts.json")
  private val benchmarkResultsFile get() = File(context.filesDir, "benchmark_results.json")

  private fun readSettings(): AppSettings {
    return try {
      if (settingsFile.exists()) {
        json.decodeFromString<AppSettings>(settingsFile.readText())
      } else {
        AppSettings()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read settings", e)
      AppSettings()
    }
  }

  private fun writeSettings(settings: AppSettings) {
    try {
      settingsFile.writeText(json.encodeToString(settings))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write settings", e)
    }
  }

  private fun readUserDataFromFile(): AppUserData {
    return try {
      if (userDataFile.exists()) {
        json.decodeFromString<AppUserData>(userDataFile.readText())
      } else {
        AppUserData()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read user data", e)
      AppUserData()
    }
  }

  private fun writeUserData(userData: AppUserData) {
    try {
      userDataFile.writeText(json.encodeToString(userData))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write user data", e)
    }
  }

  private fun readCutoutsFromFile(): AppCutoutCollection {
    return try {
      if (cutoutsFile.exists()) {
        json.decodeFromString<AppCutoutCollection>(cutoutsFile.readText())
      } else {
        AppCutoutCollection()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read cutouts", e)
      AppCutoutCollection()
    }
  }

  private fun writeCutouts(cutouts: AppCutoutCollection) {
    try {
      cutoutsFile.writeText(json.encodeToString(cutouts))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write cutouts", e)
    }
  }

  private fun readBenchmarkResultsFromFile(): AppBenchmarkResults {
    return try {
      if (benchmarkResultsFile.exists()) {
        json.decodeFromString<AppBenchmarkResults>(benchmarkResultsFile.readText())
      } else {
        AppBenchmarkResults()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read benchmark results", e)
      AppBenchmarkResults()
    }
  }

  private fun writeBenchmarkResults(results: AppBenchmarkResults) {
    try {
      benchmarkResultsFile.writeText(json.encodeToString(results))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write benchmark results", e)
    }
  }

  // --- Text Input History ---

  override fun saveTextInputHistory(history: List<String>) {
    val settings = readSettings()
    writeSettings(settings.copy(textInputHistory = history))
  }

  override fun readTextInputHistory(): List<String> {
    return readSettings().textInputHistory
  }

  // --- Theme ---

  override fun saveTheme(theme: AppTheme) {
    val settings = readSettings()
    writeSettings(settings.copy(theme = theme))
  }

  override fun readTheme(): AppTheme {
    val theme = readSettings().theme
    return if (theme == AppTheme.THEME_UNSPECIFIED) AppTheme.THEME_AUTO else theme
  }

  // --- Access Token Data ---

  override fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {
    val userData = readUserDataFromFile()
    writeUserData(
      userData.copy(
        accessTokenData = AppAccessTokenData(
          accessToken = accessToken,
          refreshToken = refreshToken,
          expiresAtMs = expiresAt,
        )
      )
    )
  }

  override fun clearAccessTokenData() {
    val userData = readUserDataFromFile()
    writeUserData(userData.copy(accessTokenData = null))
  }

  override fun readAccessTokenData(): AppAccessTokenData? {
    return readUserDataFromFile().accessTokenData
  }

  // --- Imported Models ---

  override fun saveImportedModels(importedModels: List<AppImportedModel>) {
    val settings = readSettings()
    writeSettings(settings.copy(importedModels = importedModels))
  }

  override fun readImportedModels(): List<AppImportedModel> {
    return readSettings().importedModels
  }

  // --- TOS ---

  override fun isTosAccepted(): Boolean {
    return readSettings().isTosAccepted
  }

  override fun acceptTos() {
    val settings = readSettings()
    writeSettings(settings.copy(isTosAccepted = true))
  }

  // --- Gemma Terms ---

  override fun isGemmaTermsOfUseAccepted(): Boolean {
    return readSettings().isGemmaTermsAccepted
  }

  override fun acceptGemmaTermsOfUse() {
    val settings = readSettings()
    writeSettings(settings.copy(isGemmaTermsAccepted = true))
  }

  // --- Tiny Garden ---

  override fun getHasRunTinyGarden(): Boolean {
    return readSettings().hasRunTinyGarden
  }

  override fun setHasRunTinyGarden(hasRun: Boolean) {
    val settings = readSettings()
    writeSettings(settings.copy(hasRunTinyGarden = hasRun))
  }

  // --- Cutouts ---

  override fun addCutout(cutout: AppCutout) {
    val collection = readCutoutsFromFile()
    writeCutouts(collection.copy(cutouts = collection.cutouts + cutout))
  }

  override fun getAllCutouts(): List<AppCutout> {
    return readCutoutsFromFile().cutouts
  }

  override fun setCutout(newCutout: AppCutout) {
    val collection = readCutoutsFromFile()
    val updatedCutouts = collection.cutouts.map { if (it.id == newCutout.id) newCutout else it }
    writeCutouts(collection.copy(cutouts = updatedCutouts))
  }

  override fun setCutouts(cutouts: List<AppCutout>) {
    writeCutouts(AppCutoutCollection(cutouts = cutouts))
  }

  // --- Benchmark Comparison Help ---

  override fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) {
    val settings = readSettings()
    writeSettings(settings.copy(hasSeenBenchmarkComparisonHelp = seen))
  }

  override fun getHasSeenBenchmarkComparisonHelp(): Boolean {
    return readSettings().hasSeenBenchmarkComparisonHelp
  }

  // --- Benchmark Results ---

  override fun addBenchmarkResult(result: AppBenchmarkResult) {
    val results = readBenchmarkResultsFromFile()
    writeBenchmarkResults(results.copy(results = listOf(result) + results.results))
  }

  override fun getAllBenchmarkResults(): List<AppBenchmarkResult> {
    return readBenchmarkResultsFromFile().results
  }

  override fun deleteBenchmarkResult(index: Int) {
    val results = readBenchmarkResultsFromFile()
    val mutableResults = results.results.toMutableList()
    if (index in mutableResults.indices) {
      mutableResults.removeAt(index)
    }
    writeBenchmarkResults(results.copy(results = mutableResults))
  }
}
