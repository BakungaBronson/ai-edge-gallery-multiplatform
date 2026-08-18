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

package com.google.ai.edge.gallery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.AppTheme
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions

/**
 * iOS settings dialog.
 *
 * Provides theme switching (Auto/Light/Dark).
 * OAuth and OSS licenses are omitted (Android-only features).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSettingsDialog(
  modelManagerActions: ModelManagerActions,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTheme by remember { mutableStateOf(modelManagerActions.readThemeOverride()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      // Header.
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "Settings",
          style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close")
        }
      }

      // Theme section.
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Theme",
          style = MaterialTheme.typography.titleMedium,
        )
        val themes = listOf(
          AppTheme.THEME_AUTO to "Auto",
          AppTheme.THEME_LIGHT to "Light",
          AppTheme.THEME_DARK to "Dark",
        )
        MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          themes.forEachIndexed { index, (theme, label) ->
            SegmentedButton(
              checked = selectedTheme == theme,
              onCheckedChange = {
                selectedTheme = theme
                modelManagerActions.saveThemeOverride(theme)
              },
              shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = themes.size,
              ),
            ) {
              Text(label)
            }
          }
        }
      }
    }
  }
}
