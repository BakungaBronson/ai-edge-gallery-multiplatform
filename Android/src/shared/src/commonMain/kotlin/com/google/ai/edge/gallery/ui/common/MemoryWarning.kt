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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.platform.isDeviceMemoryLow
import com.google.ai.edge.gallery.shared.resources.Res
import com.google.ai.edge.gallery.shared.resources.cancel
import com.google.ai.edge.gallery.shared.resources.memory_warning_content
import com.google.ai.edge.gallery.shared.resources.memory_warning_proceed_anyway
import com.google.ai.edge.gallery.shared.resources.memory_warning_title
import org.jetbrains.compose.resources.stringResource

/** Composable function to display a memory warning alert dialog. */
@Composable
fun MemoryWarningAlert(onProceeded: () -> Unit, onDismissed: () -> Unit) {
  AlertDialog(
    title = { Text(stringResource(Res.string.memory_warning_title)) },
    text = { Text(stringResource(Res.string.memory_warning_content)) },
    onDismissRequest = onDismissed,
    confirmButton = {
      TextButton(onClick = onProceeded) {
        Text(stringResource(Res.string.memory_warning_proceed_anyway))
      }
    },
    dismissButton = { TextButton(onClick = onDismissed) { Text(stringResource(Res.string.cancel)) } },
  )
}

/** Checks if the device's memory is lower than the required minimum for the given model. */
fun isMemoryLow(model: Model): Boolean {
  return isDeviceMemoryLow(model.minDeviceMemoryInGb)
}
