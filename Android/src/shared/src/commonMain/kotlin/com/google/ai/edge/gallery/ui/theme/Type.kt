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

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.shared.resources.Res
import com.google.ai.edge.gallery.shared.resources.nunito_black
import com.google.ai.edge.gallery.shared.resources.nunito_bold
import com.google.ai.edge.gallery.shared.resources.nunito_extrabold
import com.google.ai.edge.gallery.shared.resources.nunito_extralight
import com.google.ai.edge.gallery.shared.resources.nunito_light
import com.google.ai.edge.gallery.shared.resources.nunito_medium
import com.google.ai.edge.gallery.shared.resources.nunito_regular
import com.google.ai.edge.gallery.shared.resources.nunito_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun appFontFamily(): FontFamily = FontFamily(
  Font(Res.font.nunito_regular, FontWeight.Normal),
  Font(Res.font.nunito_extralight, FontWeight.ExtraLight),
  Font(Res.font.nunito_light, FontWeight.Light),
  Font(Res.font.nunito_medium, FontWeight.Medium),
  Font(Res.font.nunito_semibold, FontWeight.SemiBold),
  Font(Res.font.nunito_bold, FontWeight.Bold),
  Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
  Font(Res.font.nunito_black, FontWeight.Black),
)

private val baseline = Typography()

@Composable
fun AppTypography(): Typography {
  val fontFamily = appFontFamily()
  return Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = fontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = fontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = fontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = fontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = fontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = fontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = fontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = fontFamily),
  )
}

@Composable
fun titleMediumNarrow() =
  baseline.titleMedium.copy(fontFamily = appFontFamily(), letterSpacing = 0.0.sp)

@Composable
fun titleSmaller() =
  baseline.titleSmall.copy(
    fontFamily = appFontFamily(),
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
  )

@Composable
fun labelSmallNarrow() =
  baseline.labelSmall.copy(fontFamily = appFontFamily(), letterSpacing = 0.0.sp)

@Composable
fun labelSmallNarrowMedium() =
  baseline.labelSmall.copy(
    fontFamily = appFontFamily(),
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.0.sp,
  )

@Composable
fun bodySmallNarrow() =
  baseline.bodySmall.copy(fontFamily = appFontFamily(), letterSpacing = 0.0.sp)

@Composable
fun bodySmallMediumNarrow() =
  baseline.bodySmall.copy(fontFamily = appFontFamily(), letterSpacing = 0.0.sp, fontSize = 14.sp)

@Composable
fun bodySmallMediumNarrowBold() =
  baseline.bodySmall.copy(
    fontFamily = appFontFamily(),
    letterSpacing = 0.0.sp,
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
  )

@Composable
fun homePageTitleStyle() =
  baseline.displayMedium.copy(
    fontFamily = appFontFamily(),
    fontSize = 48.sp,
    lineHeight = 48.sp,
    letterSpacing = -1.sp,
    fontWeight = FontWeight.Medium,
  )

val bodyLargeNarrow = baseline.bodyLarge.copy(letterSpacing = 0.2.sp)

val headlineLargeMedium = baseline.headlineLarge.copy(fontWeight = FontWeight.Medium)
