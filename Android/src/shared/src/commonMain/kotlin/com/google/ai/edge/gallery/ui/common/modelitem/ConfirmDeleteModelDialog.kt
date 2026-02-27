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

package com.google.ai.edge.gallery.ui.common.modelitem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.shared.resources.Res
import com.google.ai.edge.gallery.shared.resources.cancel
import com.google.ai.edge.gallery.shared.resources.confirm_delete_model_dialog_content
import com.google.ai.edge.gallery.shared.resources.confirm_delete_model_dialog_title
import com.google.ai.edge.gallery.shared.resources.ok
import org.jetbrains.compose.resources.stringResource

/** Composable function to display a confirmation dialog for deleting a model. */
@Composable
fun ConfirmDeleteModelDialog(model: Model, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.confirm_delete_model_dialog_title)) },
    text = {
      Text(stringResource(Res.string.confirm_delete_model_dialog_content).replace("%s", model.name))
    },
    confirmButton = { Button(onClick = onConfirm) { Text(stringResource(Res.string.ok)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
  )
}
