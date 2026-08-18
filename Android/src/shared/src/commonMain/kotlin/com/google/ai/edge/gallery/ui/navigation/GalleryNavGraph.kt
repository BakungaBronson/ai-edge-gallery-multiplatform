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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.home.HomeScreen
import com.google.ai.edge.gallery.ui.common.tos.TosActions
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions

const val ROUTE_HOMESCREEN = "homepage"
const val ROUTE_MODEL_LIST = "model_list"
const val ROUTE_MODEL = "route_model"
const val ROUTE_BENCHMARK = "benchmark"
const val ROUTE_MODEL_MANAGER = "model_manager"

private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100

private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

private fun enterTween(): FiniteAnimationSpec<IntOffset> {
  return tween(
    ENTER_ANIMATION_DURATION_MS,
    easing = ENTER_ANIMATION_EASING,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
  )
}

private fun exitTween(): FiniteAnimationSpec<IntOffset> {
  return tween(EXIT_ANIMATION_DURATION_MS, easing = EXIT_ANIMATION_EASING)
}

private fun AnimatedContentTransitionScope<*>.slideEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
  )
}

private fun AnimatedContentTransitionScope<*>.slideExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
  )
}

private fun AnimatedContentTransitionScope<*>.slideUpEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Up,
  )
}

private fun AnimatedContentTransitionScope<*>.slideDownExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Down,
  )
}

/**
 * Provider for platform-specific screen composables.
 *
 * The shared navigation graph handles route structure and transitions,
 * while platform code supplies the actual screen implementations.
 */
interface GalleryScreenProvider {
  /** Model list screen for a specific task. */
  @Composable
  fun ModelListScreen(
    task: Task,
    modelManagerActions: ModelManagerActions,
    enableAnimation: Boolean,
    onModelClicked: (Model) -> Unit,
    navigateUp: () -> Unit,
  )

  /** Model detail/chat screen for a specific task and model. */
  @Composable
  fun ModelScreen(
    taskId: String,
    modelName: String,
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    enableModelListAnimation: () -> Unit,
  )

  /** Global model manager screen. */
  @Composable
  fun GlobalModelManagerScreen(
    modelManagerActions: ModelManagerActions,
    navigateUp: () -> Unit,
    onModelSelected: (Task, Model) -> Unit,
    onBenchmarkClicked: (Model) -> Unit,
  )

  /** Benchmark screen. */
  @Composable
  fun BenchmarkScreen(
    modelName: String,
    modelManagerActions: ModelManagerActions,
    onBackClicked: () -> Unit,
  )

  /** Settings dialog shown from HomeScreen's drawer. */
  @Composable
  fun SettingsDialog(modelManagerActions: ModelManagerActions, onDismiss: () -> Unit)

  /** Called when a task is selected on the home screen (for analytics). */
  fun onTaskSelected(task: Task) {}

  /** Called when benchmark is selected (for analytics). */
  fun onBenchmarkSelected(model: Model) {}

  /** Handle deep link navigation. */
  @Composable
  fun HandleDeepLinks(
    navController: NavHostController,
    modelManagerActions: ModelManagerActions,
  ) {}
}

/** Shared navigation host for the Gallery app. */
@Composable
fun GalleryNavHost(
  navController: NavHostController,
  modelManagerActions: ModelManagerActions,
  tosActions: TosActions,
  screenProvider: GalleryScreenProvider,
  modifier: Modifier = Modifier,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var pickedTask by remember { mutableStateOf<Task?>(null) }
  var enableHomeScreenAnimation by remember { mutableStateOf(true) }
  var enableModelListAnimation by remember { mutableStateOf(true) }

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerActions.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerActions.setAppInForeground(foreground = false)
        }
        else -> {
          /* Do nothing for other events */
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  NavHost(
    navController = navController,
    startDestination = ROUTE_HOMESCREEN,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    modifier = modifier,
  ) {
    // Home screen.
    composable(route = ROUTE_HOMESCREEN) {
      HomeScreen(
        modelManagerActions = modelManagerActions,
        tosActions = tosActions,
        enableAnimation = enableHomeScreenAnimation,
        navigateToTaskScreen = { task ->
          pickedTask = task
          enableModelListAnimation = true
          navController.navigate(ROUTE_MODEL_LIST)
          screenProvider.onTaskSelected(task)
        },
        onModelsClicked = { navController.navigate(ROUTE_MODEL_MANAGER) },
        settingsDialog = { onDismiss ->
          screenProvider.SettingsDialog(
            modelManagerActions = modelManagerActions,
            onDismiss = onDismiss,
          )
        },
      )
    }

    // Model list.
    composable(
      route = ROUTE_MODEL_LIST,
      enterTransition = {
        if (initialState.destination.route == ROUTE_HOMESCREEN) {
          slideEnter()
        } else {
          EnterTransition.None
        }
      },
      exitTransition = {
        if (targetState.destination.route == ROUTE_HOMESCREEN) {
          slideExit()
        } else {
          ExitTransition.None
        }
      },
    ) {
      pickedTask?.let { task ->
        screenProvider.ModelListScreen(
          task = task,
          modelManagerActions = modelManagerActions,
          enableAnimation = enableModelListAnimation,
          onModelClicked = { model ->
            navController.navigate("$ROUTE_MODEL/${task.id}/${model.name}")
          },
          navigateUp = {
            enableHomeScreenAnimation = false
            navController.navigateUp()
          },
        )
      }
    }

    // Model page.
    composable(
      route = "$ROUTE_MODEL/{taskId}/{modelName}",
      arguments =
        listOf(
          navArgument("taskId") { type = NavType.StringType },
          navArgument("modelName") { type = NavType.StringType },
        ),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.savedStateHandle.get<String>("modelName") ?: ""
      val taskId = backStackEntry.savedStateHandle.get<String>("taskId") ?: ""

      screenProvider.ModelScreen(
        taskId = taskId,
        modelName = modelName,
        modelManagerActions = modelManagerActions,
        navigateUp = {
          enableModelListAnimation = false
          navController.navigateUp()
        },
        enableModelListAnimation = { enableModelListAnimation = false },
      )
    }

    // Global model manager page.
    composable(
      route = ROUTE_MODEL_MANAGER,
      enterTransition = {
        if (
          initialState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            initialState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideUpEnter()
        }
      },
      exitTransition = {
        if (
          targetState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            targetState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideDownExit()
        }
      },
    ) {
      screenProvider.GlobalModelManagerScreen(
        modelManagerActions = modelManagerActions,
        navigateUp = {
          enableHomeScreenAnimation = false
          navController.navigateUp()
        },
        onModelSelected = { task, model ->
          navController.navigate("$ROUTE_MODEL/${task.id}/${model.name}")
        },
        onBenchmarkClicked = { model ->
          screenProvider.onBenchmarkSelected(model)
          navController.navigate("$ROUTE_BENCHMARK/${model.name}")
        },
      )
    }

    // Benchmark creation page.
    composable(
      route = "$ROUTE_BENCHMARK/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.savedStateHandle.get<String>("modelName") ?: ""

      screenProvider.BenchmarkScreen(
        modelName = modelName,
        modelManagerActions = modelManagerActions,
        onBackClicked = {
          enableModelListAnimation = false
          navController.navigateUp()
        },
      )
    }
  }

  // Handle deep links from platform.
  screenProvider.HandleDeepLinks(
    navController = navController,
    modelManagerActions = modelManagerActions,
  )
}
