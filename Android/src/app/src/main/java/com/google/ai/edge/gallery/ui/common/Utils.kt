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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.io.File

private const val TAG = "AGUtils"

fun Context.createTempPictureUri(
  fileName: String = "picture_${System.currentTimeMillis()}",
  fileExtension: String = ".png",
): Uri {
  val tempFile = File.createTempFile(fileName, fileExtension, cacheDir).apply { createNewFile() }

  return FileProvider.getUriForFile(
    applicationContext,
    "com.google.ai.edge.gallery.provider" /* {applicationId}.provider */,
    tempFile,
  )
}

fun checkNotificationPermissionAndStartDownload(
  context: Context,
  launcher: ManagedActivityResultLauncher<String, Boolean>,
  modelManagerViewModel: ModelManagerViewModel,
  task: Task?,
  model: Model,
) {
  // Check permission
  when (PackageManager.PERMISSION_GRANTED) {
    // Already got permission. Call the lambda.
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) -> {
      modelManagerViewModel.downloadModel(task = task, model = model)
    }

    // Otherwise, ask for permission
    else -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}
