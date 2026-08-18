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

package com.google.ai.edge.gallery.ui.modelmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.platform.PlatformBackHandler
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.modelitem.ModelItem
import kotlinx.coroutines.launch

/**
 * iOS implementation of the global model manager screen.
 *
 * Shows all models from all tasks in a single list, allowing users to browse,
 * download, and navigate to models. Does not include model import functionality
 * (Android-only feature).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosGlobalModelManager(
  modelManagerActions: ModelManagerActions,
  navigateUp: () -> Unit,
  onModelSelected: (Task, Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by modelManagerActions.uiState.collectAsState()
  val allModels = remember { mutableStateListOf<Model>() }
  val taskCandidates = remember { mutableStateListOf<Task>() }
  var modelForTaskCandidate by remember { mutableStateOf<Model?>(null) }
  var showTaskSelectorBottomSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val modelItemExpandedStates = remember { mutableStateMapOf<String, Boolean>() }

  LaunchedEffect(uiState.tasks) {
    val allModelsSet = mutableSetOf<Model>()
    for (task in uiState.tasks) {
      for (model in task.models) {
        allModelsSet.add(model)
      }
    }
    allModels.clear()
    allModels.addAll(allModelsSet.toList().sortedBy { it.displayName.ifEmpty { it.name } })
  }

  val handleClickModel: (Model) -> Unit = { model ->
    val tasks = uiState.tasks
    val tasksForModel = tasks.filter { task -> task.models.any { it.name == model.name } }
    if (tasksForModel.size == 1) {
      onModelSelected(tasksForModel[0], model)
    } else if (tasksForModel.size > 1) {
      taskCandidates.clear()
      taskCandidates.addAll(tasksForModel)
      modelForTaskCandidate = model
      showTaskSelectorBottomSheet = true
    }
  }

  PlatformBackHandler { navigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.ListAlt,
              modifier = Modifier.size(20.dp),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Models (${allModels.size})",
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.titleMedium,
            )
          }
        },
        actions = {
          IconButton(onClick = { navigateUp() }) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box {
      LazyColumn(
        modifier =
          Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = innerPadding.calculateTopPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding =
          PaddingValues(top = 16.dp, bottom = innerPadding.calculateBottomPadding() + 80.dp),
      ) {
        items(allModels) { model ->
          val expanded = modelItemExpandedStates[model.name] ?: true
          ModelItem(
            model = model,
            task = null,
            modelManagerActions = modelManagerActions,
            onModelClicked = handleClickModel,
            onBenchmarkClicked = onBenchmarkClicked,
            expanded = expanded,
            showBenchmarkButton = true,
            onExpanded = { modelItemExpandedStates[model.name] = it },
          )
        }
      }

      // Gradient overlay at the bottom.
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(innerPadding.calculateBottomPadding())
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer)
              )
            )
            .align(Alignment.BottomCenter)
      )
    }
  }

  if (showTaskSelectorBottomSheet) {
    ModalBottomSheet(
      onDismissRequest = { showTaskSelectorBottomSheet = false },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "Select task",
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp).padding(start = 16.dp),
        )
        for (task in taskCandidates) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  val model = modelForTaskCandidate
                  if (model != null) {
                    onModelSelected(task, model)
                  }
                  scope.launch {
                    sheetState.hide()
                    showTaskSelectorBottomSheet = false
                  }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
          ) {
            Text(
              task.label,
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.titleMedium,
            )
            TaskIcon(task = task, width = 40.dp)
          }
        }
      }
    }
  }
}
