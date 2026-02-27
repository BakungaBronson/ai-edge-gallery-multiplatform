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

package com.google.ai.edge.gallery.ui.common.tos

/**
 * Interface abstracting the Terms of Service operations needed by shared UI composables.
 */
interface TosActions {
  /** Check if the main app TOS has been accepted. */
  fun getIsTosAccepted(): Boolean

  /** Accept the main app TOS. */
  fun acceptTos()

  /** Check if the Gemma Terms of Use has been accepted. */
  fun getIsGemmaTermsOfUseAccepted(): Boolean

  /** Accept the Gemma Terms of Use. */
  fun acceptGemmaTermsOfUse()
}
