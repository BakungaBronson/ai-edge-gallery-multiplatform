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
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import platform.posix.gettimeofday
import platform.posix.timeval

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
  // kotlinx.datetime.Clock.System doesn't resolve on this target with this project's Kotlin/
  // kotlinx-datetime pairing (the stdlib's own kotlin.time.Clock shadows it in a way the pinned
  // kotlinx-datetime version doesn't handle here, and newer kotlinx-datetime releases remove
  // kotlinx.datetime.Clock in favor of an experimental alias — verified by actually attempting
  // both), and this Linux-hosted Kotlin/Native toolchain's bundled Foundation stub doesn't bind
  // NSDate.timeIntervalSince1970 either. gettimeofday is plain POSIX, always available
  // regardless of platform-library quirks, and gives the same epoch-millis result.
  return memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    tv.tv_sec * 1000L + tv.tv_usec / 1000L
  }
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
