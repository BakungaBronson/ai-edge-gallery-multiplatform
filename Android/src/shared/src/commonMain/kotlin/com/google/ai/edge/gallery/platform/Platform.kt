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

/**
 * Platform context abstraction. On Android this wraps android.content.Context,
 * on iOS it wraps a reference to the app's container.
 */
expect class PlatformContext

/** Returns the app's external/document files directory path. */
expect fun PlatformContext.getAppFilesDirectory(): String

/** Returns the app's cache directory path. */
expect fun PlatformContext.getCacheDirectory(): String

/** Check if the current device is a specific model (e.g., "pixel 10"). */
expect fun isDeviceModel(modelName: String): Boolean

/** Returns current time in milliseconds since epoch. */
expect fun currentTimeMillis(): Long

/** Log an analytics event with string parameters. */
expect fun logAnalyticsEvent(eventName: String, params: Map<String, String>)

/** Checks if the device's memory is lower than the required minimum for the given model. */
expect fun isDeviceMemoryLow(minDeviceMemoryInGb: Int?): Boolean

/** Platform identifier for conditional logic in shared code. */
enum class AppPlatform { ANDROID, IOS }

/** Returns the current platform. */
expect fun currentPlatform(): AppPlatform
