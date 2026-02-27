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

package com.google.ai.edge.gallery.common

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Format a float to 2 decimal places */
fun formatFloat(value: Float): String {
  val multiplied = (value * 100).roundToInt()
  val wholePart = multiplied / 100
  val decimalPart = multiplied % 100
  return "$wholePart.${decimalPart.toString().padStart(2, '0')}"
}

/** Format a float to 1 decimal place */
fun formatFloat1(value: Float): String {
  val multiplied = (value * 10).roundToInt()
  val wholePart = multiplied / 10
  val decimalPart = multiplied % 10
  return "$wholePart.$decimalPart"
}

/** Format bytes to human readable size */
fun formatHumanReadableSize(bytes: Long, si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String {
  val unit = if (si) 1000 else 1024
  if (bytes < unit) return "$bytes B"
  val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
  val value = bytes / unit.toDouble().pow(exp.toDouble())
  val formatted = if (extraDecimalForGbAndAbove && pre.lowercase() != "k" && pre != "M") {
    formatFloat(value.toFloat())
  } else {
    formatFloat1(value.toFloat())
  }
  return "$formatted ${pre}B"
}
