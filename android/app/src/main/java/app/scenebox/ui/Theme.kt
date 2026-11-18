package app.scenebox.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 统一主题：深色底 + 荧光绿主色，降低视觉噪音，卡片圆角统一 18dp。
 */
private val Green = Color(0xFF7CE38B)
private val Blue = Color(0xFF8FB7FF)
private val Amber = Color(0xFFFFC857)
private val Bg = Color(0xFF0B0F12)
private val Surface1 = Color(0xFF12181D)
private val Surface2 = Color(0xFF1A2229)
private val OnBg = Color(0xFFE7EEF4)
private val Muted = Color(0xFF9BAEBD)
private val Danger = Color(0xFFFF6B6B)

private val Scheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF06210B),
    secondary = Blue,
    tertiary = Amber,
    background = Bg,
    onBackground = OnBg,
    surface = Surface1,
    onSurface = OnBg,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Danger,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun SceneBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = AppShapes, content = content)
}
