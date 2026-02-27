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

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
actual class PlatformContext

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformContext.getAppFilesDirectory(): String {
  val paths = NSFileManager.defaultManager.URLsForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask
  )
  return (paths.firstOrNull() as? platform.Foundation.NSURL)?.path ?: ""
}

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformContext.getCacheDirectory(): String {
  val paths = NSFileManager.defaultManager.URLsForDirectory(
    NSCachesDirectory,
    NSUserDomainMask
  )
  return (paths.firstOrNull() as? platform.Foundation.NSURL)?.path ?: ""
}

@OptIn(ExperimentalForeignApi::class)
actual fun isDeviceModel(modelName: String): Boolean {
  return UIDevice.currentDevice.model.lowercase().contains(modelName.lowercase())
}

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
  return Clock.System.now().toEpochMilliseconds()
}

actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
  Napier.d(tag = "Analytics") { "Event: $eventName, params: $params" }
}

actual fun currentPlatform(): AppPlatform = AppPlatform.IOS

@OptIn(ExperimentalForeignApi::class)
actual fun isDeviceMemoryLow(minDeviceMemoryInGb: Int?): Boolean {
  if (minDeviceMemoryInGb == null) return false
  val totalMemory = NSProcessInfo.processInfo.physicalMemory
  val totalMemoryGb = totalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
  return totalMemoryGb < minDeviceMemoryInGb
}
