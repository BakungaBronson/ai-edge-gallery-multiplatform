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

package com.google.ai.edge.gallery.ui.navigation

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.isLegacyTasks
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.benchmark.BenchmarkScreen
import com.google.ai.edge.gallery.ui.common.ErrorDialog
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.chat.ModelDownloadStatusInfoPanel
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.modelmanager.GlobalModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "AGGalleryNavGraph"

/**
 * Android implementation of [GalleryScreenProvider] that provides
 * the real Android screen implementations.
 */
class AndroidScreenProvider(
  private val modelManagerViewModel: ModelManagerViewModel,
) : GalleryScreenProvider {

  @Composable
  override fun ModelListScreen(
    task: Task,
    modelManagerActions: ModelManagerActions,
    enableAnimation: Boolean,
    onModelClicked: (Model) -> Unit,
    navigateUp: () -> Unit,
  ) {
    ModelManager(
      modelManagerActions = modelManagerViewModel,
      task = task,
      enableAnimation = enableAnimation,
      onModelClicked = onModelClicked,
      navigateUp = navigateUp,
    )
  }

  @Composable
  override fun ModelScreen(
    taskId: String,
    modelName: String,
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    enableModelListAnimation: () -> Unit,
  ) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
      LaunchedEffect(Unit) { modelManagerViewModel.selectModel(model) }

      val customTask = modelManagerViewModel.getCustomTaskByTaskId(id = taskId)
      if (customTask != null) {
        if (isLegacyTasks(customTask.task.id)) {
          customTask.MainScreen(
            data =
              CustomTaskDataForBuiltinTask(
                modelManagerActions = modelManagerViewModel,
                onNavUp = {
                  enableModelListAnimation()
                  navigateUp()
                },
              )
          )
        } else {
          var disableAppBarControls by remember { mutableStateOf(false) }
          var hideTopBar by remember { mutableStateOf(false) }
          var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
          CustomTaskScreen(
            task = customTask.task,
            modelManagerViewModel = modelManagerViewModel,
            onNavigateUp = {
              if (customNavigateUpCallback != null) {
                customNavigateUpCallback?.invoke()
              } else {
                enableModelListAnimation()
                navigateUp()

                // clean up all models.
                scope.launch(Dispatchers.Default) {
                  for (m in customTask.task.models) {
                    modelManagerViewModel.cleanupModel(
                      context = context,
                      task = customTask.task,
                      model = m,
                    )
                  }
                }
              }
            },
            disableAppBarControls = disableAppBarControls,
            hideTopBar = hideTopBar,
            useThemeColor = customTask.task.useThemeColor,
          ) { bottomPadding ->
            customTask.MainScreen(
              data =
                CustomTaskData(
                  modelManagerActions = modelManagerViewModel,
                  bottomPadding = bottomPadding,
                  setAppBarControlsDisabled = { disableAppBarControls = it },
                  setTopBarVisible = { hideTopBar = !it },
                  setCustomNavigateUpCallback = { customNavigateUpCallback = it },
                )
            )
          }
        }
      }
    }
  }

  @Composable
  override fun GlobalModelManagerScreen(
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    onModelSelected: (Task, Model) -> Unit,
    onBenchmarkClicked: (Model) -> Unit,
  ) {
    GlobalModelManager(
      viewModel = modelManagerViewModel,
      navigateUp = navigateUp,
      onModelSelected = onModelSelected,
      onBenchmarkClicked = onBenchmarkClicked,
    )
  }

  @Composable
  override fun BenchmarkScreen(
    modelName: String,
    modelManagerActions: ModelManagerActions,
    onBackClicked: () -> Unit,
  ) {
    modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
      BenchmarkScreen(
        initialModel = model,
        modelManagerViewModel = modelManagerViewModel,
        onBackClicked = onBackClicked,
      )
    }
  }

  @Composable
  override fun SettingsDialog(modelManagerActions: ModelManagerActions, onDismiss: () -> Unit) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = onDismiss,
    )
  }

  override fun onTaskSelected(task: Task) {
    firebaseAnalytics?.logEvent(
      GalleryEvent.CAPABILITY_SELECT.id,
      Bundle().apply { putString("capability_name", task.id) },
    )
  }

  override fun onBenchmarkSelected(model: Model) {
    firebaseAnalytics?.logEvent(
      GalleryEvent.CAPABILITY_SELECT.id,
      Bundle().apply { putString("capability_name", "benchmark_${model.name}") },
    )
  }

  @Composable
  override fun HandleDeepLinks(
    navController: NavHostController,
    modelManagerActions: ModelManagerActions,
  ) {
    // Handle incoming intents for deep links
    val intent = androidx.activity.compose.LocalActivity.current?.intent
    val data = intent?.data
    if (data != null) {
      intent.data = null
      Log.d(TAG, "navigation link clicked: $data")
      if (data.toString().startsWith("com.google.ai.edge.gallery://model/")) {
        if (data.pathSegments.size >= 2) {
          val taskId = data.pathSegments.get(data.pathSegments.size - 2)
          val modelName = data.pathSegments.last()
          modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
            navController.navigate("$ROUTE_MODEL/${taskId}/${model.name}")
          }
        } else {
          Log.e(TAG, "Malformed deep link URI received: $data")
        }
      } else if (data.toString() == "com.google.ai.edge.gallery://global_model_manager") {
        navController.navigate(ROUTE_MODEL_MANAGER)
      }
    }
  }
}

/**
 * Android entry point that creates the shared [GalleryNavHost] with Android screen implementations.
 */
@Composable
fun AndroidGalleryNavHost(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val tosViewModel: com.google.ai.edge.gallery.ui.common.tos.TosViewModel = koinViewModel()
  val screenProvider = remember(modelManagerViewModel) { AndroidScreenProvider(modelManagerViewModel) }

  GalleryNavHost(
    navController = navController,
    modelManagerActions = modelManagerViewModel,
    tosActions = tosViewModel,
    screenProvider = screenProvider,
    modifier = modifier,
  )
}

@Composable
private fun CustomTaskScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  disableAppBarControls: Boolean,
  hideTopBar: Boolean,
  useThemeColor: Boolean,
  onNavigateUp: () -> Unit,
  content: @Composable (bottomPadding: Dp) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var navigatingUp by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }
  var appBarHeight by remember { mutableIntStateOf(0) }

  val handleNavigateUp = {
    navigatingUp = true
    onNavigateUp()
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(
          TAG,
          "Initializing model '${selectedModel.name}' from CustomTaskScreen launched effect",
        )
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  Scaffold(
    topBar = {
      AnimatedVisibility(
        !hideTopBar,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
      ) {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          inProgress = disableAppBarControls,
          modelPreparing = disableAppBarControls,
          canShowResetSessionButton = false,
          useThemeColor = useThemeColor,
          modifier =
            Modifier.onGloballyPositioned { coordinates -> appBarHeight = coordinates.size.height },
          hideModelSelector = task.models.size <= 1,
          onConfigChanged = { _, _ -> },
          onBackClicked = { handleNavigateUp() },
          onModelSelected = { prevModel, newSelectedModel ->
            scope.launch(Dispatchers.Default) {
              // Clean up prev model.
              if (prevModel.name != newSelectedModel.name) {
                modelManagerViewModel.cleanupModel(
                  context = context,
                  task = task,
                  model = prevModel,
                )
              }

              // Update selected model.
              modelManagerViewModel.selectModel(model = newSelectedModel)
            }
          },
        )
      }
    }
  ) { innerPadding ->
    // Calculate the target height in Dp for the content's top padding.
    val targetPaddingDp =
      if (!hideTopBar && appBarHeight > 0) {
        // Convert measured pixel height to Dp
        with(LocalDensity.current) { appBarHeight.toDp() }
      } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      }

    // Animate the actual top padding value.
    val animatedTopPadding by
      animateDpAsState(
        targetValue = targetPaddingDp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TopPaddingAnimation",
      )

    Box(
      modifier =
        Modifier.padding(
          top = if (!hideTopBar) innerPadding.calculateTopPadding() else animatedTopPadding,
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
      AnimatedContent(
        targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      ) { targetState ->
        when (targetState) {
          // Main UI when model is downloaded.
          true -> content(innerPadding.calculateBottomPadding())
          // Model download
          false ->
            ModelDownloadStatusInfoPanel(
              model = selectedModel,
              task = task,
              modelManagerActions = modelManagerViewModel,
            )
        }
      }
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = {
        showErrorDialog = false
        onNavigateUp()
      },
    )
  }
}
