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

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSCachesDirectory
import platform.UIKit.UIDevice

actual class PlatformContext

actual fun PlatformContext.getAppFilesDirectory(): String {
  val paths = NSFileManager.defaultManager.URLsForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask
  )
  return (paths.firstOrNull() as? platform.Foundation.NSURL)?.path ?: ""
}

actual fun PlatformContext.getCacheDirectory(): String {
  val paths = NSFileManager.defaultManager.URLsForDirectory(
    NSCachesDirectory,
    NSUserDomainMask
  )
  return (paths.firstOrNull() as? platform.Foundation.NSURL)?.path ?: ""
}

actual fun isDeviceModel(modelName: String): Boolean {
  return UIDevice.currentDevice.model.lowercase().contains(modelName.lowercase())
}

actual fun currentTimeMillis(): Long =
  (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun logAnalyticsEvent(eventName: String, params: Map<String, String>) {
  // No-op on iOS. Can be implemented with Firebase iOS SDK later.
}

actual fun currentPlatform(): AppPlatform = AppPlatform.IOS

actual fun isDeviceMemoryLow(minDeviceMemoryInGb: Int?): Boolean {
  if (minDeviceMemoryInGb == null) return false
  val totalMemory = platform.Foundation.NSProcessInfo.processInfo.physicalMemory
  val totalMemoryGb = totalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
  return totalMemoryGb < minDeviceMemoryInGb
}
