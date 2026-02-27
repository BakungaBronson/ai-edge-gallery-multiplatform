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

package com.google.ai.edge.gallery.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data class for a task displayed on the home screen.
 *
 * Tasks are grouped into categories (see [category] field), which correspond to the tabs on the
 * home screen. The tab bar is hidden if only one category exists. Each task can have a list of
 * associated models (see [Model]), which are shown when the task is selected.
 */
data class Task(
  val id: String,
  val label: String,
  val category: CategoryInfo,
  val icon: ImageVector? = null,
  val iconVectorResourceId: Int? = null,
  val description: String,
  val docUrl: String = "",
  val sourceCodeUrl: String = "",
  val models: MutableList<Model>,
  val modelNames: List<String> = listOf(),
  val handleModelConfigChangesInTask: Boolean = false,
  val experimental: Boolean = false,
  val useThemeColor: Boolean = false,

  /** String key for the agent name (replaces Android @StringRes). */
  val agentNameKey: String = "chat_generic_agent_name",

  /** String key for the text input placeholder (replaces Android @StringRes). */
  val textInputPlaceHolderKey: String = "chat_textinput_placeholder",

  // Managed by the app.
  var index: Int = -1,
  val updateTrigger: MutableState<Long> = mutableLongStateOf(0),
)

object BuiltInTaskId {
  const val LLM_CHAT = "llm_chat"
  const val LLM_PROMPT_LAB = "llm_prompt_lab"
  const val LLM_ASK_IMAGE = "llm_ask_image"
  const val LLM_ASK_AUDIO = "llm_ask_audio"
  const val LLM_MOBILE_ACTIONS = "llm_mobile_actions"
  const val LLM_TINY_GARDEN = "llm_tiny_garden"
  const val MP_SCRAPBOOK = "mp_scrapbook"
}

private val allLegacyTaskIds: Set<String> =
  setOf(
    BuiltInTaskId.LLM_CHAT,
    BuiltInTaskId.LLM_PROMPT_LAB,
    BuiltInTaskId.LLM_ASK_IMAGE,
    BuiltInTaskId.LLM_ASK_AUDIO,
  )

fun isLegacyTasks(id: String): Boolean {
  return allLegacyTaskIds.contains(id)
}
