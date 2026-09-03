package com.pablobertino.tagmap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp),
)

/** Dirección "Instrumentos": datos y etiquetas en monoespaciada. */
val InstrumentTypography = Typography.copy(
    titleLarge = Typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
    bodySmall = Typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp),
    labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp),
)
