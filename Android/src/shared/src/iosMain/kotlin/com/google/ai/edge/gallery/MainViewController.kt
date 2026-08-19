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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.ui.common.tos.TosActions
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import com.google.ai.edge.gallery.ui.navigation.GalleryNavHost
import com.google.ai.edge.gallery.ui.navigation.GalleryScreenProvider
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import org.koin.mp.KoinPlatform.getKoin

/** Creates the main UIViewController hosting the Compose Multiplatform UI. */
fun MainViewController() = ComposeUIViewController {
  val koin = remember { getKoin() }
  val modelManagerActions = remember { koin.get<ModelManagerActions>() }
  val tosActions = remember { koin.get<TosActions>() }
  val screenProvider = remember { koin.get<GalleryScreenProvider>() }

  // Nothing else triggers this on iOS, and ModelManagerUiState.loadingModelAllowlist starts
  // true — so without this the app sits on "Loading model list..." forever and no model is ever
  // reachable. Android does the same thing from MainActivity.onCreate; this is the iOS
  // equivalent entry point.
  LaunchedEffect(Unit) { modelManagerActions.loadModelAllowlist() }

  GalleryTheme {
    val navController = rememberNavController()
    GalleryNavHost(
      navController = navController,
      modelManagerActions = modelManagerActions,
      tosActions = tosActions,
      screenProvider = screenProvider,
    )
  }
}
