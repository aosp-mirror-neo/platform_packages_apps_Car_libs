/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.oem.tokens.compose.compat.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.car.oem.tokens.R
import com.android.car.oem.tokens.Token

@Composable
fun oemColorScheme(oemContext: Context): ColorScheme {
    val oemColorPrimary = Token.getColor(oemContext, R.attr.oemColorPrimary)
    val oemColorOnPrimary = Token.getColor(oemContext, R.attr.oemColorOnPrimary)
    val oemColorPrimaryContainer = Token.getColor(oemContext, R.attr.oemColorPrimaryContainer)
    val oemColorOnPrimaryContainer = Token.getColor(oemContext, R.attr.oemColorOnPrimaryContainer)
    val oemColorSecondary = Token.getColor(oemContext, R.attr.oemColorSecondary)
    val oemColorOnSecondary = Token.getColor(oemContext, R.attr.oemColorOnSecondary)
    val oemColorSecondaryContainer = Token.getColor(oemContext, R.attr.oemColorSecondaryContainer)
    val oemColorOnSecondaryContainer =
        Token.getColor(oemContext, R.attr.oemColorOnSecondaryContainer)
    val oemColorTertiary = Token.getColor(oemContext, R.attr.oemColorTertiary)
    val oemColorOnTertiary = Token.getColor(oemContext, R.attr.oemColorOnTertiary)
    val oemColorTertiaryContainer = Token.getColor(oemContext, R.attr.oemColorTertiaryContainer)
    val oemColorOnTertiaryContainer = Token.getColor(oemContext, R.attr.oemColorOnTertiaryContainer)
    val oemColorError = Token.getColor(oemContext, R.attr.oemColorError)
    val oemColorOnError = Token.getColor(oemContext, R.attr.oemColorOnError)
    val oemColorErrorContainer = Token.getColor(oemContext, R.attr.oemColorErrorContainer)
    val oemColorOnErrorContainer = Token.getColor(oemContext, R.attr.oemColorOnErrorContainer)
    val oemColorBackground = Token.getColor(oemContext, R.attr.oemColorBackground)
    val oemColorOnBackground = Token.getColor(oemContext, R.attr.oemColorOnBackground)
    val oemColorSurface = Token.getColor(oemContext, R.attr.oemColorSurface)
    val oemColorOnSurface = Token.getColor(oemContext, R.attr.oemColorOnSurface)
    val oemColorSurfaceVariant = Token.getColor(oemContext, R.attr.oemColorSurfaceVariant)
    val oemColorOnSurfaceVariant = Token.getColor(oemContext, R.attr.oemColorOnSurfaceVariant)
    val oemColorOutline = Token.getColor(oemContext, R.attr.oemColorOutline)
    val oemColorSurfaceInverse = Token.getColor(oemContext, R.attr.oemColorSurfaceInverse)
    val oemColorOnSurfaceInverse = Token.getColor(oemContext, R.attr.oemColorOnSurfaceInverse)
    val oemColorPrimaryInverse = Token.getColor(oemContext, R.attr.oemColorPrimaryInverse)
    val oemColorOutlineVariant = Token.getColor(oemContext, R.attr.oemColorOutlineVariant)
    val oemColorScrim = Token.getColor(oemContext, R.attr.oemColorScrim)
    val oemColorSurfaceTint = Token.getColor(oemContext, R.attr.oemColorSurfaceTint)
    val oemColorSurfaceDim = Token.getColor(oemContext, R.attr.oemColorSurfaceDim)
    val oemColorSurfaceBright = Token.getColor(oemContext, R.attr.oemColorSurfaceBright)
    val oemColorSurfaceContainer = Token.getColor(oemContext, R.attr.oemColorSurfaceContainer)
    val oemColorSurfaceContainerHigh =
        Token.getColor(oemContext, R.attr.oemColorSurfaceContainerHigh)
    val oemColorSurfaceContainerHighest =
        Token.getColor(oemContext, R.attr.oemColorSurfaceContainerHighest)
    val oemColorSurfaceContainerLow = Token.getColor(oemContext, R.attr.oemColorSurfaceContainerLow)
    val oemColorSurfaceContainerLowest =
        Token.getColor(oemContext, R.attr.oemColorSurfaceContainerLowest)

    return ColorScheme(
        primary = Color(oemColorPrimary),
        onPrimary = Color(oemColorOnPrimary),
        primaryContainer = Color(oemColorPrimaryContainer),
        onPrimaryContainer = Color(oemColorOnPrimaryContainer),
        inversePrimary = Color(oemColorPrimaryInverse),
        secondary = Color(oemColorSecondary),
        onSecondary = Color(oemColorOnSecondary),
        secondaryContainer = Color(oemColorSecondaryContainer),
        onSecondaryContainer = Color(oemColorOnSecondaryContainer),
        tertiary = Color(oemColorTertiary),
        onTertiary = Color(oemColorOnTertiary),
        tertiaryContainer = Color(oemColorTertiaryContainer),
        onTertiaryContainer = Color(oemColorOnTertiaryContainer),
        background = Color(oemColorBackground),
        onBackground = Color(oemColorOnBackground),
        surface = Color(oemColorSurface),
        onSurface = Color(oemColorOnSurface),
        surfaceVariant = Color(oemColorSurfaceVariant),
        onSurfaceVariant = Color(oemColorOnSurfaceVariant),
        surfaceTint = Color(oemColorSurfaceTint),
        inverseSurface = Color(oemColorSurfaceInverse),
        inverseOnSurface = Color(oemColorOnSurfaceInverse),
        error = Color(oemColorError),
        onError = Color(oemColorOnError),
        errorContainer = Color(oemColorErrorContainer),
        onErrorContainer = Color(oemColorOnErrorContainer),
        outline = Color(oemColorOutline),
        outlineVariant = Color(oemColorOutlineVariant),
        scrim = Color(oemColorScrim),
        surfaceBright = Color(oemColorSurfaceBright),
        surfaceDim = Color(oemColorSurfaceDim),
        surfaceContainer = Color(oemColorSurfaceContainer),
        surfaceContainerHigh = Color(oemColorSurfaceContainerHigh),
        surfaceContainerHighest = Color(oemColorSurfaceContainerHighest),
        surfaceContainerLow = Color(oemColorSurfaceContainerLow),
        surfaceContainerLowest = Color(oemColorSurfaceContainerLowest),
    )
}

@Composable
fun oemTypography(oemContext: Context): Typography {
    val oemTextAppearanceDisplayLarge =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceDisplayLarge)
    val oemTextAppearanceDisplayMedium =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceDisplayMedium)
    val oemTextAppearanceDisplaySmall =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceDisplaySmall)
    val oemTextAppearanceHeadlineLarge =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceHeadlineLarge)
    val oemTextAppearanceHeadlineMedium =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceHeadlineMedium)
    val oemTextAppearanceHeadlineSmall =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceHeadlineSmall)
    val oemTextAppearanceTitleLarge =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceTitleLarge)
    val oemTextAppearanceTitleMedium =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceTitleMedium)
    val oemTextAppearanceTitleSmall =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceTitleSmall)
    val oemTextAppearanceBodyLarge =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceBodyLarge)
    val oemTextAppearanceBodyMedium =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceBodyMedium)
    val oemTextAppearanceBodySmall =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceBodySmall)
    val oemTextAppearanceLabelLarge =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceLabelLarge)
    val oemTextAppearanceLabelMedium =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceLabelMedium)
    val oemTextAppearanceLabelSmall =
        Token.getTextAppearance(oemContext, R.attr.oemTextAppearanceLabelSmall)

    return Typography(
        displayLarge = oemTextStyle(oemContext, oemTextAppearanceDisplayLarge),
        displayMedium = oemTextStyle(oemContext, oemTextAppearanceDisplayMedium),
        displaySmall = oemTextStyle(oemContext, oemTextAppearanceDisplaySmall),
        headlineLarge = oemTextStyle(oemContext, oemTextAppearanceHeadlineLarge),
        headlineMedium = oemTextStyle(oemContext, oemTextAppearanceHeadlineMedium),
        headlineSmall = oemTextStyle(oemContext, oemTextAppearanceHeadlineSmall),
        titleLarge = oemTextStyle(oemContext, oemTextAppearanceTitleLarge),
        titleMedium = oemTextStyle(oemContext, oemTextAppearanceTitleMedium),
        titleSmall = oemTextStyle(oemContext, oemTextAppearanceTitleSmall),
        bodyLarge = oemTextStyle(oemContext, oemTextAppearanceBodyLarge),
        bodyMedium = oemTextStyle(oemContext, oemTextAppearanceBodyMedium),
        bodySmall = oemTextStyle(oemContext, oemTextAppearanceBodySmall),
        labelLarge = oemTextStyle(oemContext, oemTextAppearanceLabelLarge),
        labelMedium = oemTextStyle(oemContext, oemTextAppearanceLabelMedium),
        labelSmall = oemTextStyle(oemContext, oemTextAppearanceLabelSmall),
    )
}

fun oemTextStyle(oemContext: Context, textStyle: Int): TextStyle {
    // keep these sorted by id
    val attrs =
        intArrayOf(
            android.R.attr.textSize, // 16842901
            android.R.attr.textStyle, // 16842903
            android.R.attr.textColor, // 16842904
            android.R.attr.letterSpacing, // 16843958
            android.R.attr.lineHeight, // 16844159
        )

    val ta: TypedArray = oemContext.obtainStyledAttributes(textStyle, attrs)
    val textSize = ta.getDimension(0, 0f)
    val textStyleType = ta.getInt(1, 0)
    val textColor = ta.getColor(2, 0)
    val letterSpacing = ta.getFloat(3, 0f)
    val lineHeight = ta.getDimension(4, 0f)
    var fontStyle = FontStyle.Normal
    var fontWeight = FontWeight.Normal

    if (textStyleType == 1) {
        fontWeight = FontWeight.Bold
    } else if (textStyleType == 2) {
        fontStyle = FontStyle.Italic
    }

    ta.recycle()

    return TextStyle(
        fontSize = textSize.sp,
        letterSpacing = letterSpacing.sp,
        lineHeight = lineHeight.sp,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        color = Color(textColor),
    )
}

@Composable fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

@Composable
fun oemShapes(oemContext: Context): Shapes {
    val oemShapeCornerExtraSmall =
        Token.getCornerRadius(oemContext, R.attr.oemShapeCornerExtraSmall)
    val oemShapeCornerSmall =
        Token.getCornerRadius(oemContext, R.attr.oemShapeCornerSmall).toInt().pxToDp()
    val oemShapeCornerMedium =
        Token.getCornerRadius(oemContext, R.attr.oemShapeCornerMedium).toInt().pxToDp()
    val oemShapeCornerLarge =
        Token.getCornerRadius(oemContext, R.attr.oemShapeCornerLarge).toInt().pxToDp()
    val oemShapeCornerExtraLarge =
        Token.getCornerRadius(oemContext, R.attr.oemShapeCornerExtraLarge).toInt().pxToDp()

    return Shapes(
        extraSmall = RoundedCornerShape(oemShapeCornerExtraSmall),
        small = RoundedCornerShape(oemShapeCornerSmall),
        medium = RoundedCornerShape(oemShapeCornerMedium),
        large = RoundedCornerShape(oemShapeCornerLarge),
        extraLarge = RoundedCornerShape(oemShapeCornerExtraLarge),
    )
}

@Composable
fun OemTokenTheme(content: @Composable() () -> Unit) {
    val view = LocalView.current
    val context = view.context as Activity
    val oemContext = Token.createOemStyledContext(context)

    MaterialTheme(
        colorScheme = oemColorScheme(oemContext),
        typography = oemTypography(oemContext),
        shapes = oemShapes(oemContext),
        content = content,
    )
}
