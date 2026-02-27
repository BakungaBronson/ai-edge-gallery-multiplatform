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

/**
 * Stores basic info about a Category.
 *
 * A category is a tab on the home page which contains a list of tasks. Category is set through
 * Task.
 */
data class CategoryInfo(
  val id: String,
  /** String label for the category. */
  val label: String = "",
)

/** Pre-defined categories. */
object Category {
  val LLM = CategoryInfo(id = "llm", label = "LLM")
  val CLASSICAL_ML = CategoryInfo(id = "classical_ml", label = "Classical ML")
  val EXPERIMENTAL = CategoryInfo(id = "experimental", label = "Experimental")
}
