package ai.storytold.craftanything

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object CraftColorSchemes {
    val Light = lightColorScheme(
        primary = Color(0xFF7049D5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFECE5FF),
        onPrimaryContainer = Color(0xFF2B1857),
        secondary = Color(0xFF64558E),
        onSecondary = Color.White,
        tertiary = Color(0xFF8A5E0C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFEFCB),
        onTertiaryContainer = Color(0xFF3D2B08),
        background = Color(0xFFF7F6FA),
        onBackground = Color(0xFF211E29),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF211E29),
        surfaceVariant = Color(0xFFF0EDF5),
        onSurfaceVariant = Color(0xFF6D6878),
        outline = Color(0xFFD8D3E2),
        error = Color(0xFFB32643),
        onError = Color.White,
        errorContainer = Color(0xFFFFE9EE),
        onErrorContainer = Color(0xFF68182C)
    )

    val Dark = darkColorScheme(
        primary = Color(0xFFB59AFF),
        onPrimary = Color(0xFF2A145D),
        primaryContainer = Color(0xFF3B2866),
        onPrimaryContainer = Color(0xFFEDE4FF),
        secondary = Color(0xFFC9B9F5),
        tertiary = Color(0xFFFFD38D),
        tertiaryContainer = Color(0xFF3A3021),
        onTertiaryContainer = Color(0xFFFFE7B9),
        background = Color(0xFF101015),
        surface = Color(0xFF191820),
        surfaceVariant = Color(0xFF25232D),
        onSurface = Color(0xFFF3F0F8),
        onSurfaceVariant = Color(0xFFB8B2C3),
        outline = Color(0xFF484451),
        error = Color(0xFFFFA3B0),
        errorContainer = Color(0xFF542B35),
        onErrorContainer = Color(0xFFFFD9DF)
    )
}
