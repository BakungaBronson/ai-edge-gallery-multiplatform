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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.shared.resources.Res
import com.google.ai.edge.gallery.shared.resources.cd_stop_icon
import com.google.ai.edge.gallery.shared.resources.download
import com.google.ai.edge.gallery.shared.resources.try_it
import com.google.ai.edge.gallery.ui.common.tos.GemmaTermsOfUseDialog
import com.google.ai.edge.gallery.ui.common.tos.TosActions
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * iOS actual implementation of DownloadAndTryButton.
 *
 * Simplified version without OAuth, HuggingFace token exchange, or notification permissions.
 * Handles download, cancel, try-it, and Gemma TOS flows directly.
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
  val tosActions: TosActions = koinInject()
  var showMemoryWarning by remember { mutableStateOf(false) }
  var showGemmaTermsOfUseDialog by remember { mutableStateOf(false) }

  val needToDownloadFirst =
    (downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED ||
      downloadStatus?.status == ModelDownloadStatusType.FAILED) &&
      model.localFileRelativeDirPathOverride.isEmpty()
  val inProgress = downloadStatus?.status == ModelDownloadStatusType.IN_PROGRESS
  val downloadSucceeded = downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  val isPartiallyDownloaded = downloadStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED
  val isUnzipping = downloadStatus?.status == ModelDownloadStatusType.UNZIPPING
  val showDownloadProgress = !downloadSucceeded && (inProgress || isPartiallyDownloaded || isUnzipping)

  val startDownload = {
    modelManagerActions.downloadModel(task = task, model = model)
  }

  val handleClickButton = {
    if (needToDownloadFirst) {
      startDownload()
    } else {
      onClicked()
    }
  }

  val checkMemoryAndClickDownloadButton = {
    if (isMemoryLow(model = model)) {
      showMemoryWarning = true
    } else {
      handleClickButton()
    }
  }

  if (!showDownloadProgress) {
    var buttonModifier: Modifier = modifier.height(42.dp)
    if (!compact) {
      buttonModifier = buttonModifier.then(modifierWhenExpanded)
    }
    Button(
      modifier = buttonModifier,
      colors =
        ButtonDefaults.buttonColors(
          containerColor =
            if (
              (!downloadSucceeded || !canShowTryIt) &&
                model.localFileRelativeDirPathOverride.isEmpty()
            ) {
              MaterialTheme.colorScheme.surfaceContainer
            } else if (task != null) {
              getTaskBgGradientColors(task = task)[1]
            } else {
              MaterialTheme.colorScheme.primary
            }
        ),
      contentPadding = PaddingValues(horizontal = 12.dp),
      onClick = {
        if (!enabled) {
          return@Button
        }

        // Check TOS before downloading.
        if (
          model.url.startsWith("https://dl.google.com/google-ai-edge-gallery/") &&
            !tosActions.getIsGemmaTermsOfUseAccepted()
        ) {
          showGemmaTermsOfUseDialog = true
        } else {
          checkMemoryAndClickDownloadButton()
        }
      },
    ) {
      val textColor =
        if (!downloadSucceeded && model.localFileRelativeDirPathOverride.isEmpty()) {
          MaterialTheme.colorScheme.onSurface
        } else if (task != null) {
          Color.White
        } else {
          MaterialTheme.colorScheme.onPrimary
        }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          if (needToDownloadFirst) Icons.Outlined.FileDownload
          else Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = textColor,
        )

        if (!compact) {
          if (needToDownloadFirst) {
            Text(
              stringResource(Res.string.download),
              color = textColor,
              style = MaterialTheme.typography.titleMedium,
            )
          } else if (canShowTryIt) {
            Text(
              stringResource(Res.string.try_it),
              color = textColor,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
  // Download progress.
  else {
    var curDownloadProgress =
      downloadStatus!!.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()
    if (curDownloadProgress.isNaN()) {
      curDownloadProgress = 0f
    }
    val animatedProgress = remember { Animatable(0f) }

    var downloadProgressModifier: Modifier = modifier
    if (!compact) {
      downloadProgressModifier = downloadProgressModifier.fillMaxWidth()
    }
    downloadProgressModifier =
      downloadProgressModifier
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 8.dp)
        .height(42.dp)
    Row(modifier = downloadProgressModifier, verticalAlignment = Alignment.CenterVertically) {
      if (isUnzipping) {
        Text(
          "Unzipping...",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = if (!compact) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 4.dp),
        )
      } else {
        Text(
          "${(curDownloadProgress * 100).toInt()}%",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 12.dp).width(if (compact) 32.dp else 44.dp),
        )
        if (!compact) {
          val color =
            if (task != null) getTaskBgGradientColors(task = task)[1]
            else MaterialTheme.colorScheme.primary
          LinearProgressIndicator(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            progress = { animatedProgress.value },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          )
        }
        val cbStop = stringResource(Res.string.cd_stop_icon)
        IconButton(
          onClick = {
            modelManagerActions.cancelDownloadModel(model = model)
          },
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
          modifier = Modifier.semantics { contentDescription = cbStop },
        ) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
    LaunchedEffect(curDownloadProgress) {
      animatedProgress.animateTo(curDownloadProgress, animationSpec = tween(150))
    }
  }

  if (showMemoryWarning) {
    MemoryWarningAlert(
      onProceeded = {
        handleClickButton()
        showMemoryWarning = false
      },
      onDismissed = { showMemoryWarning = false },
    )
  }

  if (showGemmaTermsOfUseDialog) {
    GemmaTermsOfUseDialog(
      onTosAccepted = {
        showGemmaTermsOfUseDialog = false
        tosActions.acceptGemmaTermsOfUse()
        checkMemoryAndClickDownloadButton()
      },
      onCancel = { showGemmaTermsOfUseDialog = false },
    )
  }
}
