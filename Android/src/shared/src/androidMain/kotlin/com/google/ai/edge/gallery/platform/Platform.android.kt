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

package com.google.ai.edge.gallery.platform

import android.content.Context
import android.os.Build

actual class PlatformContext(val context: Context)

actual fun PlatformContext.getAppFilesDirectory(): String {
  return context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
}

actual fun PlatformContext.getCacheDirectory(): String {
  return context.cacheDir.absolutePath
}

actual fun isDeviceModel(modelName: String): Boolean {
  return Build.MODEL?.lowercase()?.contains(modelName.lowercase()) == true
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun isDeviceMemoryLow(minDeviceMemoryInGb: Int?): Boolean {
  if (minDeviceMemoryInGb == null) return false
  return try {
    val context = com.google.firebase.FirebaseApp.getInstance().applicationContext
    val activityManager =
      context.getSystemService(android.app.Activity.ACTIVITY_SERVICE) as? android.app.ActivityManager
    if (activityManager != null) {
      val memoryInfo = android.app.ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memoryInfo)
      val bytesInGb = 1024f * 1024 * 1024
      var deviceMemInGb = memoryInfo.totalMem / bytesInGb
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        deviceMemInGb = memoryInfo.advertisedMem / bytesInGb
      }
      deviceMemInGb < minDeviceMemoryInGb
    } else {
      false
    }
  } catch (_: Exception) {
    false
  }
}

actual fun currentPlatform(): AppPlatform = AppPlatform.ANDROID

actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
  try {
    val analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(
      com.google.firebase.FirebaseApp.getInstance().applicationContext
    )
    val bundle = android.os.Bundle().apply {
      params.forEach { (key, value) -> putString(key, value) }
    }
    analytics.logEvent(eventName, bundle)
  } catch (_: Exception) {
    // Firebase may not be available (e.g., missing google-services.json)
  }
}
