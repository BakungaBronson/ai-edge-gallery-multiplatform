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

import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.data.IosDataStoreRepository
import com.google.ai.edge.gallery.data.IosDownloadRepository
import com.google.ai.edge.gallery.inference.IosLlmDelegate
import com.google.ai.edge.gallery.inference.IosLlmInferenceEngine
import com.google.ai.edge.gallery.inference.LlmInferenceEngine
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Called from Swift to initialize Koin for iOS.
 *
 * @param llmDelegate Swift-side LLM delegate implementing [IosLlmDelegate]
 *   (e.g., MediaPipeLlmDelegate using MediaPipeTasksGenAI).
 */
fun initKoinIos(llmDelegate: IosLlmDelegate) {
  startKoin {
    modules(
      module {
        single<DataStoreRepository> { IosDataStoreRepository() }
        single<DownloadRepository> { IosDownloadRepository(dataStoreRepository = get()) }
        single<LlmInferenceEngine> { IosLlmInferenceEngine(llmDelegate) }
      }
    )
  }
}
