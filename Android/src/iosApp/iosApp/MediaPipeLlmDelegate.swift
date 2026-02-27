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

import Foundation
import shared

/// Swift implementation of IosLlmDelegate using MediaPipe iOS SDK.
///
/// To enable LLM inference on iOS:
/// 1. Add MediaPipeTasksGenAI via Swift Package Manager
/// 2. Implement the methods below using MediaPipe's LlmInference API
/// 3. Register this delegate with Koin in iOSApp.swift
///
/// Reference: https://github.com/2BAB/mediapiper (Swift delegate pattern)
class MediaPipeLlmDelegate: IosLlmDelegate {

    func initialize(
        modelPath: String,
        backend: String,
        maxTokens: Int32,
        cacheDir: String?,
        onDone: @escaping (String) -> Void
    ) {
        // TODO: Initialize MediaPipe LlmInference with the given model path.
        //
        // Example (pseudocode):
        //   let options = LlmInference.Options(modelPath: modelPath)
        //   options.maxTokens = Int(maxTokens)
        //   self.inference = try LlmInference(options: options)
        //
        onDone("LLM inference not yet implemented on iOS")
    }

    func createConversation(
        topK: Int32,
        topP: Double,
        temperature: Double,
        systemInstruction: String?
    ) -> IosLlmConversationDelegate {
        return MediaPipeConversationDelegate()
    }

    func close() {
        // TODO: Release MediaPipe resources.
    }
}

/// Swift implementation of IosLlmConversationDelegate.
class MediaPipeConversationDelegate: IosLlmConversationDelegate {

    func sendMessageAsync(
        text: String,
        imageBytes: [KotlinByteArray],
        audioBytes: [KotlinByteArray],
        onToken: @escaping (String) -> Void,
        onDone: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        // TODO: Implement streaming inference via MediaPipe.
        //
        // Example (pseudocode):
        //   inference.generateResponseAsync(inputText: text) { partialResult, error in
        //     if let error = error {
        //       onError(error.localizedDescription)
        //       return
        //     }
        //     if let text = partialResult {
        //       onToken(text)
        //     }
        //   } completion: {
        //     onDone()
        //   }
        //
        onError("LLM inference not yet implemented on iOS")
    }

    func sendMessage(
        text: String,
        imageBytes: [KotlinByteArray],
        audioBytes: [KotlinByteArray]
    ) -> String {
        // TODO: Implement synchronous inference.
        return "LLM inference not yet implemented on iOS"
    }

    func cancel() {
        // TODO: Cancel ongoing inference.
    }

    func close() {
        // TODO: Release conversation resources.
    }
}
