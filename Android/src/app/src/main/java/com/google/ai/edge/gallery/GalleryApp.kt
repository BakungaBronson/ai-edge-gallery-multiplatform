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

package com.google.ai.edge.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.common.DownloadAndTryButton
import com.google.ai.edge.gallery.ui.common.DownloadAndTryButtonDelegate
import com.google.ai.edge.gallery.ui.common.LocalDownloadAndTryButtonDelegate
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.navigation.AndroidGalleryNavHost

/** Top level composable representing the main screen of the application. */
@Composable
fun GalleryApp(
  navController: NavHostController = rememberNavController(),
  modelManagerViewModel: ModelManagerViewModel,
) {
  CompositionLocalProvider(
    LocalDownloadAndTryButtonDelegate provides DownloadAndTryButtonDelegate {
        task, model, enabled, downloadStatus, _, onClicked, modifier, modifierWhenExpanded, compact, canShowTryIt ->
      // Delegate to the app's Android-specific DownloadAndTryButton with full OAuth support.
      DownloadAndTryButton(
        task = task,
        model = model,
        enabled = enabled,
        downloadStatus = downloadStatus,
        modelManagerViewModel = modelManagerViewModel,
        onClicked = onClicked,
        modifier = modifier,
        modifierWhenExpanded = modifierWhenExpanded,
        compact = compact,
        canShowTryIt = canShowTryIt,
      )
    }
  ) {
    AndroidGalleryNavHost(
      navController = navController,
      modelManagerViewModel = modelManagerViewModel,
    )
  }
}
