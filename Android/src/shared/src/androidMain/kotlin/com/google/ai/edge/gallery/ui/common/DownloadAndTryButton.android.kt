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

package com.google.ai.edge.gallery.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions

/**
 * Delegate interface for the Android-specific DownloadAndTryButton implementation.
 *
 * The app module provides the real implementation (with OAuth, token exchange, etc.)
 * via [LocalDownloadAndTryButtonDelegate].
 */
fun interface DownloadAndTryButtonDelegate {
  @Composable
  fun Content(
    task: Task?,
    model: Model,
    enabled: Boolean,
    downloadStatus: ModelDownloadStatus?,
    modelManagerActions: ModelManagerActions,
    onClicked: () -> Unit,
    modifier: Modifier,
    modifierWhenExpanded: Modifier,
    compact: Boolean,
    canShowTryIt: Boolean,
  )
}

/**
 * CompositionLocal for the Android DownloadAndTryButton delegate.
 * Must be provided by the app module via CompositionLocalProvider.
 */
val LocalDownloadAndTryButtonDelegate = staticCompositionLocalOf<DownloadAndTryButtonDelegate> {
  error("No DownloadAndTryButtonDelegate provided. Did you forget to wrap with CompositionLocalProvider?")
}

/**
 * Android actual delegates to the app-provided implementation via [LocalDownloadAndTryButtonDelegate].
 */
@Composable
actual fun DownloadAndTryButton(
  task: Task?,
  model: Model,
  enabled: Boolean,
  downloadStatus: ModelDownloadStatus?,
  modelManagerActions: ModelManagerActions,
  onClicked: () -> Unit,
  modifier: Modifier,
  modifierWhenExpanded: Modifier,
  compact: Boolean,
  canShowTryIt: Boolean,
) {
  LocalDownloadAndTryButtonDelegate.current.Content(
    task = task,
    model = model,
    enabled = enabled,
    downloadStatus = downloadStatus,
    modelManagerActions = modelManagerActions,
    onClicked = onClicked,
    modifier = modifier,
    modifierWhenExpanded = modifierWhenExpanded,
    compact = compact,
    canShowTryIt = canShowTryIt,
  )
}
