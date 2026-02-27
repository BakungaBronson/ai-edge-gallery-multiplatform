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

package com.google.ai.edge.gallery.customtasks.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions

/**
 * Data class to hold information passed to the `MainScreen` composable of a custom task.
 *
 * @param modelManagerActions The actions interface providing access to model management.
 * @param bottomPadding The bottom padding of the Scaffold's `innerPadding`. By default, your
 *   `MainScreen` will extend to the bottom edge. Use this value if you need to apply padding to the
 *   bottom of your screen's content to account for elements like a bottom navigation bar.
 * @param setAppBarControlsDisabled A callback function that the custom task screen can call to
 *   enable and disable controls (e.g. back button, configs, etc) in the app bar.
 * @param setTopBarVisible A callback function that the custom task screen can call to show and hide
 *   the top bar.
 */
data class CustomTaskData(
  val modelManagerActions: ModelManagerActions,
  val bottomPadding: Dp = 0.dp,
  val setAppBarControlsDisabled: (Boolean) -> Unit = {},
  val setTopBarVisible: (Boolean) -> Unit = {},
  val setCustomNavigateUpCallback: ((() -> Unit)?) -> Unit = {},
)

data class CustomTaskDataForBuiltinTask(
  val modelManagerActions: ModelManagerActions,
  val onNavUp: () -> Unit,
)
