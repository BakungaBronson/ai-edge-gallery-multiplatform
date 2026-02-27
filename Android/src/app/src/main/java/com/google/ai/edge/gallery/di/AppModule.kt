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

package com.google.ai.edge.gallery.di

import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.GalleryLifecycleProvider
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.mobileactions.MobileActionsTask
import com.google.ai.edge.gallery.customtasks.tinygarden.TinyGardenTask
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DefaultDataStoreRepository
import com.google.ai.edge.gallery.data.DefaultDownloadRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.ui.benchmark.BenchmarkViewModel
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.HoldToDictateViewModel
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmAskAudioTask
import com.google.ai.edge.gallery.ui.llmchat.LlmAskAudioViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageTask
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmChatTask
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import com.google.ai.edge.gallery.ui.llmsingleturn.LlmSingleTurnTask
import com.google.ai.edge.gallery.ui.llmsingleturn.LlmSingleTurnViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.customtasks.examplecustomtask.ExampleCustomTaskViewModel
import com.google.ai.edge.gallery.customtasks.mobileactions.MobileActionsViewModel
import com.google.ai.edge.gallery.customtasks.tinygarden.TinyGardenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
  // AppLifecycleProvider
  single<AppLifecycleProvider> { GalleryLifecycleProvider() }

  // DataStoreRepository (JSON-based)
  single<DataStoreRepository> { DefaultDataStoreRepository(androidContext()) }

  // DownloadRepository
  single<DownloadRepository> { DefaultDownloadRepository(androidContext(), get()) }

  // CustomTasks (replaces Hilt @IntoSet multibinding)
  single<Set<CustomTask>> {
    setOf(
      LlmChatTask(),
      LlmAskImageTask(),
      LlmAskAudioTask(),
      LlmSingleTurnTask(),
      MobileActionsTask(),
      TinyGardenTask(),
    )
  }

  // ViewModels
  viewModel {
    ModelManagerViewModel(
      downloadRepository = get(),
      dataStoreRepository = get(),
      lifecycleProvider = get(),
      customTasks = get(),
      context = androidContext(),
    )
  }

  viewModel {
    BenchmarkViewModel(
      appContext = androidContext(),
      dataStoreRepository = get(),
    )
  }

  viewModel { TosViewModel(dataStoreRepository = get()) }

  viewModel { HoldToDictateViewModel(context = androidContext()) }

  viewModel { LlmChatViewModel() }
  viewModel { LlmAskImageViewModel() }
  viewModel { LlmAskAudioViewModel() }
  viewModel { LlmSingleTurnViewModel() }
  viewModel { ExampleCustomTaskViewModel() }

  viewModel {
    MobileActionsViewModel(appContext = androidContext())
  }

  viewModel {
    TinyGardenViewModel(
      context = androidContext(),
      dataStoreRepository = get(),
    )
  }
}
