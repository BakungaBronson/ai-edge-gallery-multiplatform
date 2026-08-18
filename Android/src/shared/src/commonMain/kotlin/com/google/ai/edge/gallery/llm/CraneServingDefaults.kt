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

package com.google.ai.edge.gallery.llm

/**
 * The Crane a065 serving system prompt (language-match + Ugandan-context).
 *
 * Single source of truth for two uses: the prefilled default shown in the per-model settings
 * sheet's system-prompt editor (see [com.google.ai.edge.gallery.data.createLlmChatConfigs]) and
 * the fallback used at conversation creation if a model's config value is ever missing (see
 * `LlmChatModelHelper`). Lives in commonMain (not the Android-only `CraneConversationCompat.kt`
 * it used to live in) so both `:shared`'s config UI and `:app`'s Android chat path can use the
 * same string without one depending on the other.
 */
const val CRANE_SYSTEM_PROMPT =
  "You are an assistant for Ugandan primary-school teachers. Reply in the " +
    "SAME language as the teacher's message: if the teacher writes in " +
    "English, reply in English. Only write in Luganda for the specific " +
    "content the teacher explicitly asks to be in Luganda (for example, a " +
    "Luganda reading passage or Luganda example words). Always use Ugandan " +
    "context in your examples — local names (Nakato, Kato, Wasswa, " +
    "Nalwanga), local items (matooke, jerrycans, bottle tops, beans), and " +
    "Ugandan places."
