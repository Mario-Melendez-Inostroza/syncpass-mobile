package com.android.syncpass.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun estilo(tamano: Int, linea: Int, peso: FontWeight, espacio: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = peso,
    fontSize = tamano.sp,
    lineHeight = linea.sp,
    letterSpacing = espacio.sp,
)

/** Títulos más firmes que los de Material por defecto; el cuerpo conserva tamaños cómodos de leer en terreno. */
val Typography = Typography(
    displaySmall = estilo(36, 44, FontWeight.ExtraBold, -0.5),
    headlineLarge = estilo(32, 40, FontWeight.ExtraBold, -0.25),
    headlineMedium = estilo(28, 36, FontWeight.Bold),
    headlineSmall = estilo(24, 32, FontWeight.Bold),
    titleLarge = estilo(22, 28, FontWeight.Bold),
    titleMedium = estilo(17, 24, FontWeight.SemiBold, 0.1),
    titleSmall = estilo(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = estilo(16, 24, FontWeight.Normal, 0.3),
    bodyMedium = estilo(14, 21, FontWeight.Normal, 0.2),
    labelLarge = estilo(14, 20, FontWeight.SemiBold, 0.1),
)
