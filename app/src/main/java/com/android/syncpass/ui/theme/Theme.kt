package com.android.syncpass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsquemaClaro = lightColorScheme(
    primary = Azul600,
    onPrimary = Color.White,
    primaryContainer = Azul100,
    onPrimaryContainer = Color(0xFF0A2A6B),
    secondary = Color(0xFF3B5BA9),
    onSecondary = Color.White,
    secondaryContainer = Azul100,
    onSecondaryContainer = Color(0xFF0A2A6B),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F5EF),
    onTertiaryContainer = Color(0xFF00403A),
    background = FondoClaro,
    onBackground = Azul900,
    surface = Color.White,
    onSurface = Azul900,
    surfaceVariant = Color(0xFFE3ECFF),
    onSurfaceVariant = Color(0xFF4F5F8A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF8D9BC0),
    outlineVariant = Color(0xFFD3DEF5),
    error = Color(0xFFD93025),
    errorContainer = Color(0xFFFDE4E1),
)

/** Modo oscuro en azul marino, no negro: mantiene la identidad de la marca. */
private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF7DB0FF),
    onPrimary = Color(0xFF06214F),
    primaryContainer = Azul700,
    onPrimaryContainer = Azul100,
    secondary = Color(0xFFA9C4FF),
    onSecondary = Color(0xFF0B2352),
    secondaryContainer = Color(0xFF22386E),
    onSecondaryContainer = Azul100,
    tertiary = Color(0xFF6FD8C8),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF16524A),
    onTertiaryContainer = Color(0xFFC6F4EC),
    background = FondoOscuro,
    onBackground = Color(0xFFE6ECFF),
    surface = SuperficieOscura,
    onSurface = Color(0xFFE6ECFF),
    surfaceVariant = Color(0xFF1E2C58),
    onSurfaceVariant = Color(0xFFA9B7DB),
    surfaceContainerLowest = Color(0xFF0E1838),
    surfaceContainerLow = SuperficieOscura,
    surfaceContainer = SuperficieOscura,
    surfaceContainerHigh = Color(0xFF182650),
    surfaceContainerHighest = Color(0xFF182650),
    outline = Color(0xFF6B7DAA),
    outlineVariant = Color(0xFF2B3B6B),
    error = Color(0xFFFFB4AB),
)

private val Formas = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/**
 * Tema de SyncPass. Claro por defecto (como la web); el oscuro se activa en Perfil. No se usan los colores dinámicos
 * del fondo de pantalla: con ellos la app cambiaba de aspecto según el teléfono y no mostraba la marca.
 */
@Composable
fun SyncPassTheme(
    oscuro: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        typography = Typography,
        shapes = Formas,
        content = content,
    )
}
