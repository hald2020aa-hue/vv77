package eu.kanade.tachiyomi.ui.enhanced

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val healthColors = darkColorScheme(
    primary = Color(0xFF9D3CF0),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0C0A19),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF15132D),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF211B43),
    onSurfaceVariant = Color(0xFFC2B9DF),
    secondary = Color(0xFF19E6FF),
    onSecondary = Color(0xFF08111D),
    outline = Color(0xFF7559B3),
    error = Color(0xFFFF6477),
)

@Composable
internal fun EnhancedReadingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = healthColors, content = content)
}
