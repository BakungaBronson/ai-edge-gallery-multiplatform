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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS implementation of [DataStoreRepository] using JSON files
 * stored in the app's Documents directory.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDataStoreRepository : DataStoreRepository {
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
  private val fileManager = NSFileManager.defaultManager

  private val documentsDir: String by lazy {
    val paths = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    (paths.firstOrNull() as? NSURL)?.path ?: ""
  }

  private fun filePath(name: String): String = "$documentsDir/$name"

  private fun readString(fileName: String): String? {
    val path = filePath(fileName)
    return if (fileManager.fileExistsAtPath(path)) {
      NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    } else {
      null
    }
  }

  private fun writeString(fileName: String, content: String) {
    val nsString = NSString.create(string = content)
    nsString.writeToFile(filePath(fileName), atomically = true, encoding = NSUTF8StringEncoding, error = null)
  }

  // --- Text input history ---

  override fun saveTextInputHistory(history: List<String>) {
    writeString("text_input_history.json", json.encodeToString(history))
  }

  override fun readTextInputHistory(): List<String> {
    val raw = readString("text_input_history.json") ?: return emptyList()
    return try {
      json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- Theme ---

  override fun saveTheme(theme: AppTheme) {
    writeString("theme.json", json.encodeToString(theme))
  }

  override fun readTheme(): AppTheme {
    val raw = readString("theme.json") ?: return AppTheme.THEME_AUTO
    return try {
      json.decodeFromString<AppTheme>(raw)
    } catch (_: Exception) {
      AppTheme.THEME_AUTO
    }
  }

  // --- Access token ---

  override fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {
    val data = AppAccessTokenData(
      accessToken = accessToken,
      refreshToken = refreshToken,
      expiresAtMs = expiresAt,
    )
    writeString("access_token.json", json.encodeToString(data))
  }

  override fun clearAccessTokenData() {
    val path = filePath("access_token.json")
    if (fileManager.fileExistsAtPath(path)) {
      fileManager.removeItemAtPath(path, null)
    }
  }

  override fun readAccessTokenData(): AppAccessTokenData? {
    val raw = readString("access_token.json") ?: return null
    return try {
      json.decodeFromString<AppAccessTokenData>(raw)
    } catch (_: Exception) {
      null
    }
  }

  // --- Imported models ---

  override fun saveImportedModels(importedModels: List<AppImportedModel>) {
    writeString("imported_models.json", json.encodeToString(importedModels))
  }

  override fun readImportedModels(): List<AppImportedModel> {
    val raw = readString("imported_models.json") ?: return emptyList()
    return try {
      json.decodeFromString<List<AppImportedModel>>(raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- TOS ---

  override fun isTosAccepted(): Boolean {
    val raw = readString("tos.json") ?: return false
    return raw.trim() == "true"
  }

  override fun acceptTos() {
    writeString("tos.json", "true")
  }

  override fun isGemmaTermsOfUseAccepted(): Boolean {
    val raw = readString("gemma_tos.json") ?: return false
    return raw.trim() == "true"
  }

  override fun acceptGemmaTermsOfUse() {
    writeString("gemma_tos.json", "true")
  }

  // --- TinyGarden ---

  override fun getHasRunTinyGarden(): Boolean {
    val raw = readString("tiny_garden.json") ?: return false
    return raw.trim() == "true"
  }

  override fun setHasRunTinyGarden(hasRun: Boolean) {
    writeString("tiny_garden.json", hasRun.toString())
  }

  // --- Cutouts ---

  override fun addCutout(cutout: AppCutout) {
    val cutouts = getAllCutouts().toMutableList()
    cutouts.add(cutout)
    setCutouts(cutouts)
  }

  override fun getAllCutouts(): List<AppCutout> {
    val raw = readString("cutouts.json") ?: return emptyList()
    return try {
      json.decodeFromString<List<AppCutout>>(raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  override fun setCutout(newCutout: AppCutout) {
    val cutouts = getAllCutouts().toMutableList()
    val index = cutouts.indexOfFirst { it.id == newCutout.id }
    if (index >= 0) {
      cutouts[index] = newCutout
    } else {
      cutouts.add(newCutout)
    }
    setCutouts(cutouts)
  }

  override fun setCutouts(cutouts: List<AppCutout>) {
    writeString("cutouts.json", json.encodeToString(cutouts))
  }

  // --- Benchmark ---

  override fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) {
    writeString("benchmark_help_seen.json", seen.toString())
  }

  override fun getHasSeenBenchmarkComparisonHelp(): Boolean {
    val raw = readString("benchmark_help_seen.json") ?: return false
    return raw.trim() == "true"
  }

  override fun addBenchmarkResult(result: AppBenchmarkResult) {
    val results = getAllBenchmarkResults().toMutableList()
    results.add(0, result)
    writeString("benchmark_results.json", json.encodeToString(results))
  }

  override fun getAllBenchmarkResults(): List<AppBenchmarkResult> {
    val raw = readString("benchmark_results.json") ?: return emptyList()
    return try {
      json.decodeFromString<List<AppBenchmarkResult>>(raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  override fun deleteBenchmarkResult(index: Int) {
    val results = getAllBenchmarkResults().toMutableList()
    if (index in results.indices) {
      results.removeAt(index)
      writeString("benchmark_results.json", json.encodeToString(results))
    }
  }
}
