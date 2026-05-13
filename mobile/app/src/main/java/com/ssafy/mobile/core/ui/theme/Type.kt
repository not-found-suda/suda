@file:Suppress("MagicNumber")

package com.ssafy.mobile.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SudaFontFamily: FontFamily = FontFamily.Default

val Typography =
    Typography(
        displayLarge =
            sudaTextStyle(
                weight = FontWeight.Bold,
                fontSize = 44,
                lineHeight = 52,
            ),
        displayMedium =
            sudaTextStyle(
                weight = FontWeight.Bold,
                fontSize = 36,
                lineHeight = 44,
            ),
        displaySmall =
            sudaTextStyle(
                weight = FontWeight.Bold,
                fontSize = 30,
                lineHeight = 38,
            ),
        headlineLarge =
            sudaTextStyle(
                weight = FontWeight.Bold,
                fontSize = 28,
                lineHeight = 36,
            ),
        headlineMedium =
            sudaTextStyle(
                weight = FontWeight.SemiBold,
                fontSize = 24,
                lineHeight = 32,
            ),
        headlineSmall =
            sudaTextStyle(
                weight = FontWeight.SemiBold,
                fontSize = 22,
                lineHeight = 30,
            ),
        titleLarge =
            sudaTextStyle(
                weight = FontWeight.SemiBold,
                fontSize = 20,
                lineHeight = 28,
            ),
        titleMedium =
            sudaTextStyle(
                weight = FontWeight.SemiBold,
                fontSize = 18,
                lineHeight = 26,
            ),
        titleSmall =
            sudaTextStyle(
                weight = FontWeight.Medium,
                fontSize = 16,
                lineHeight = 24,
            ),
        bodyLarge =
            sudaTextStyle(
                weight = FontWeight.Normal,
                fontSize = 16,
                lineHeight = 24,
            ),
        bodyMedium =
            sudaTextStyle(
                weight = FontWeight.Normal,
                fontSize = 14,
                lineHeight = 21,
            ),
        bodySmall =
            sudaTextStyle(
                weight = FontWeight.Normal,
                fontSize = 12,
                lineHeight = 18,
            ),
        labelLarge =
            sudaTextStyle(
                weight = FontWeight.SemiBold,
                fontSize = 14,
                lineHeight = 20,
            ),
        labelMedium =
            sudaTextStyle(
                weight = FontWeight.Medium,
                fontSize = 12,
                lineHeight = 16,
            ),
        labelSmall =
            sudaTextStyle(
                weight = FontWeight.Medium,
                fontSize = 11,
                lineHeight = 14,
            ),
    )

private fun sudaTextStyle(
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
): TextStyle =
    TextStyle(
        fontFamily = SudaFontFamily,
        fontWeight = weight,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp,
    )
