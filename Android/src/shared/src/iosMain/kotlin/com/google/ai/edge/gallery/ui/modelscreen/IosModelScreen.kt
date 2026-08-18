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

package com.google.ai.edge.gallery.ui.modelscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.inference.LlmContent
import com.google.ai.edge.gallery.inference.LlmConversationConfig
import com.google.ai.edge.gallery.inference.LlmGenerationOptions
import com.google.ai.edge.gallery.inference.LlmInferenceEngine
import com.google.ai.edge.gallery.inference.LlmMessageCallback
import com.google.ai.edge.gallery.llm.CRANE_SYSTEM_PROMPT
import com.google.ai.edge.gallery.platform.IosImagePicker
import com.google.ai.edge.gallery.platform.PlatformBackHandler
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.chat.ModelDownloadStatusInfoPanel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerActions
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.koin.compose.koinInject

private sealed class ChatItem {
  data class UserMessage(
    val text: String,
    val imageBytes: List<ByteArray> = emptyList(),
  ) : ChatItem()

  data class ModelMessage(val text: String, val inProgress: Boolean = false) : ChatItem()
  data class Error(val message: String) : ChatItem()
}

/**
 * iOS model screen that handles download, initialization, and basic LLM chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosModelScreen(
  task: Task,
  model: Model,
  modelManagerActions: ModelManagerActions,
  navigateUp: () -> Unit,
) {
  val uiState by modelManagerActions.uiState.collectAsState()
  val downloadStatus = uiState.modelDownloadStatus[model.name]
  val isDownloaded = downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  // Hoisted out of the chat panel so the app bar can disable the model-configs button while a
  // response is streaming — editing a config there can force a re-initialization, which would
  // pull the native engine out from under an in-flight generation.
  var isGenerating by remember { mutableStateOf(false) }

  PlatformBackHandler { navigateUp() }

  Scaffold(
    topBar = {
      ModelPageAppBar(
        task = task,
        model = model,
        modelManagerViewModel = modelManagerActions,
        onBackClicked = navigateUp,
        onModelSelected = { _, _ -> },
        inProgress = isGenerating,
        modelPreparing = uiState.isModelInitializing(model),
      )
    },
  ) { innerPadding ->
    AnimatedContent(
      targetState = isDownloaded,
      modifier = Modifier.padding(innerPadding),
    ) { downloaded ->
      if (downloaded) {
        IosLlmChatPanel(
          task = task,
          model = model,
          modelManagerActions = modelManagerActions,
          isGenerating = isGenerating,
          onGeneratingChanged = { isGenerating = it },
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        ModelDownloadStatusInfoPanel(
          model = model,
          task = task,
          modelManagerActions = modelManagerActions,
        )
      }
    }
  }
}

/**
 * LLM chat panel for iOS using the shared LlmInferenceEngine.
 * Supports text chat and optional image attachments for vision-capable models.
 */
@Composable
private fun IosLlmChatPanel(
  task: Task,
  model: Model,
  modelManagerActions: ModelManagerActions,
  isGenerating: Boolean,
  onGeneratingChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val engine: LlmInferenceEngine = koinInject()
  val scope = rememberCoroutineScope()
  val chatItems = remember { mutableStateListOf<ChatItem>() }
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val attachedImages = remember { mutableStateListOf<ByteArray>() }
  val supportsImages = model.llmSupportImage

  // Initialization is delegated to the model manager rather than driven here. It already does
  // exactly this work (reads MAX_TOKENS/ACCELERATOR, builds LlmEngineConfig, initializes the
  // engine), and — the reason this matters — it is the only thing that publishes
  // modelInitializationStatus. ModelPageAppBar gates the model-configs button on
  // `isModelInitialized`, so while the panel initialized the engine privately the settings
  // sheet was rendered permanently greyed out on iOS and the decoding guards were unreachable.
  val uiState by modelManagerActions.uiState.collectAsState()
  val initStatus = uiState.modelInitializationStatus[model.name]
  val isModelReady = initStatus?.status == ModelInitializationStatusType.INITIALIZED
  val initError =
    initStatus?.takeIf { it.status == ModelInitializationStatusType.ERROR }?.error?.ifEmpty {
      "Unknown initialization error"
    }
  val isInitializing = !isModelReady && initError == null

  LaunchedEffect(model.name) {
    modelManagerActions.initializeModel(task = task, model = model)
  }

  DisposableEffect(model.name) {
    onDispose {
      modelManagerActions.cleanupModel(task = task, model = model)
    }
  }

  Column(modifier = modifier.imePadding()) {
    if (isInitializing) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Text(
            "Initializing model...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
          )
        }
      }
    } else if (initError != null) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(32.dp),
        ) {
          Text(
            "Failed to initialize model",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
          )
          Text(
            initError ?: "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    } else {
      // Chat messages.
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
          horizontal = 16.dp,
          vertical = 8.dp,
        ),
      ) {
        if (chatItems.isEmpty()) {
          item {
            Box(
              modifier = Modifier.fillParentMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                if (supportsImages) "Send a message or image to start chatting"
                else "Send a message to start chatting",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        items(chatItems.toList()) { item ->
          when (item) {
            is ChatItem.UserMessage -> {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
              ) {
                // Show attached images.
                if (item.imageBytes.isNotEmpty()) {
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                  ) {
                    items(item.imageBytes) { bytes ->
                      val imageBitmap = remember(bytes) {
                        try {
                          SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                        } catch (_: Exception) {
                          null
                        }
                      }
                      if (imageBitmap != null) {
                        Image(
                          bitmap = imageBitmap,
                          contentDescription = "Attached image",
                          modifier = Modifier
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                          contentScale = ContentScale.Fit,
                        )
                      }
                    }
                  }
                }
                Text(
                  item.text,
                  style = MaterialTheme.typography.bodyLarge,
                  modifier = Modifier.padding(start = 48.dp).padding(8.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }

            is ChatItem.ModelMessage -> {
              Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                  item.text.ifEmpty { if (item.inProgress) "..." else "" },
                  style = MaterialTheme.typography.bodyLarge,
                  modifier = Modifier.padding(end = 48.dp).padding(8.dp),
                )
              }
            }

            is ChatItem.Error -> {
              Text(
                item.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp),
              )
            }
          }
        }
      }

      // Scroll to bottom when new messages arrive.
      LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
          listState.animateScrollToItem(chatItems.size - 1)
        }
      }
    }

    // Attached images preview.
    if (attachedImages.isNotEmpty()) {
      LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(attachedImages.toList()) { bytes ->
          Box {
            val imageBitmap = remember(bytes) {
              try {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
              } catch (_: Exception) {
                null
              }
            }
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = "Attached image",
                modifier = Modifier
                  .size(64.dp)
                  .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
              )
            }
            // Remove button.
            Icon(
              Icons.Rounded.Close,
              contentDescription = "Remove image",
              modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd)
                .background(
                  MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                  RoundedCornerShape(10.dp),
                )
                .clickable { attachedImages.remove(bytes) },
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    // Input area.
    if (!isInitializing && initError == null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Reset button.
        IconButton(
          onClick = {
            chatItems.clear()
            attachedImages.clear()
          },
          enabled = !isGenerating && (chatItems.isNotEmpty() || attachedImages.isNotEmpty()),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Reset chat")
        }

        // Image attach button (only for vision-capable models).
        if (supportsImages) {
          IconButton(
            onClick = {
              IosImagePicker.pickImage { bytes ->
                if (bytes != null) {
                  attachedImages.add(bytes)
                }
              }
            },
            enabled = !isGenerating,
          ) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Attach image")
          }
        }

        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("Type a message...") },
          enabled = !isGenerating,
          maxLines = 4,
        )

        // Send button.
        IconButton(
          onClick = {
            val messageText = inputText.trim()
            if (messageText.isEmpty() && attachedImages.isEmpty()) return@IconButton

            val currentImages = attachedImages.toList()
            inputText = ""
            attachedImages.clear()
            chatItems.add(ChatItem.UserMessage(messageText, currentImages))
            chatItems.add(ChatItem.ModelMessage("", inProgress = true))
            onGeneratingChanged(true)

            scope.launch {
              try {
                val topK = model.getIntConfigValue(
                  key = ConfigKeys.TOPK,
                  defaultValue = DEFAULT_TOPK,
                )
                val topP = model.getFloatConfigValue(
                  key = ConfigKeys.TOPP,
                  defaultValue = DEFAULT_TOPP,
                )
                val temperature = model.getFloatConfigValue(
                  key = ConfigKeys.TEMPERATURE,
                  defaultValue = DEFAULT_TEMPERATURE,
                )
                // The system prompt is applied template-safely at conversation creation, the
                // same place the Android C-API path applies it.
                val systemPrompt = model.getStringConfigValue(
                  key = ConfigKeys.SYSTEM_PROMPT,
                  defaultValue = CRANE_SYSTEM_PROMPT,
                )
                val conversation = engine.createConversation(
                  LlmConversationConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                    systemInstruction =
                      if (systemPrompt.isBlank()) null
                      else listOf(LlmContent.Text(systemPrompt)),
                  )
                )

                // Decoding guards, read per-send from the model config so the settings sheet
                // takes effect on the next message without re-initializing. Mirrors
                // LlmChatModelHelper.generationOptions() on Android.
                val options = LlmGenerationOptions(
                  maxOutputTokens = model.getIntConfigValue(
                    key = ConfigKeys.MAX_TOKENS,
                    defaultValue =
                      if (model.llmMaxToken > 0) model.llmMaxToken else DEFAULT_MAX_TOKEN,
                  ),
                  repetitionPenalty = model.getFloatConfigValue(
                    key = ConfigKeys.REPETITION_PENALTY,
                    defaultValue = LlmGenerationOptions.DEFAULT_REPETITION_PENALTY,
                  ),
                  noRepeatNgramSize = model.getIntConfigValue(
                    key = ConfigKeys.NO_REPEAT_NGRAM,
                    defaultValue = LlmGenerationOptions.DEFAULT_NO_REPEAT_NGRAM_SIZE,
                  ),
                )

                // Build content list with text and optional images.
                val contents = mutableListOf<LlmContent>()
                for (imageBytes in currentImages) {
                  contents.add(LlmContent.ImageBytes(imageBytes))
                }
                if (messageText.isNotEmpty()) {
                  contents.add(LlmContent.Text(messageText))
                }

                val responseIndex = chatItems.size - 1
                conversation.sendMessageAsync(
                  contents = contents,
                  options = options,
                  callback = object : LlmMessageCallback {
                    override fun onMessage(text: String) {
                      val current = chatItems[responseIndex]
                      if (current is ChatItem.ModelMessage) {
                        chatItems[responseIndex] = current.copy(
                          text = current.text + text,
                        )
                      }
                    }

                    override fun onDone() {
                      val current = chatItems[responseIndex]
                      if (current is ChatItem.ModelMessage) {
                        chatItems[responseIndex] = current.copy(inProgress = false)
                      }
                      onGeneratingChanged(false)
                      // Each send builds its own conversation (so an edited system prompt takes
                      // effect immediately); close it or the native handle leaks per message.
                      conversation.close()
                    }

                    override fun onError(throwable: Throwable) {
                      chatItems[responseIndex] = ChatItem.Error(
                        throwable.message ?: "Unknown error"
                      )
                      onGeneratingChanged(false)
                      conversation.close()
                    }
                  }
                )
              } catch (e: Exception) {
                chatItems.add(ChatItem.Error(e.message ?: "Failed to send message"))
                onGeneratingChanged(false)
              }
            }
          },
          enabled = !isGenerating && (inputText.isNotBlank() || attachedImages.isNotEmpty()),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
        }
      }
    }
  }
}
