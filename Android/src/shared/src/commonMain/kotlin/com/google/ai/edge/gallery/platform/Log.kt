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

package com.google.ai.edge.gallery.platform

import io.github.aakira.napier.Napier

/** Multiplatform logging facade backed by Napier. */
object Log {
  fun d(tag: String, message: String) {
    Napier.d(message, tag = tag)
  }

  fun e(tag: String, message: String, throwable: Throwable? = null) {
    Napier.e(message, throwable, tag = tag)
  }

  fun i(tag: String, message: String) {
    Napier.i(message, tag = tag)
  }

  fun w(tag: String, message: String) {
    Napier.w(message, tag = tag)
  }
}
