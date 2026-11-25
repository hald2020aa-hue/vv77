package eu.kanade.tachiyomi.ui.enhanced

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val healthColors = darkColorScheme(
    primary = Color(0xFFFF4FA3),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF070B16),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF101A2C),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFFA7B4C8),
    secondary = Color(0xFF48DDF2),
    onSecondary = Color(0xFF08111D),
    outline = Color(0xFF304861),
    error = Color(0xFFFF6477),
)

@Composable
internal fun EnhancedReadingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = healthColors, content = content)
}
