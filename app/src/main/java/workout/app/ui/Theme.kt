package workout.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFD6F25C),
    onPrimary = Color(0xFF1A1F0C),
    secondary = Color(0xFFB7C9A4),
    onSecondary = Color(0xFF1A1F0C),
    background = Color(0xFF0B0D0A),
    onBackground = Color(0xFFE7EDE0),
    surface = Color(0xFF343D30),
    onSurface = Color(0xFFE7EDE0),
    surfaceVariant = Color(0xFF4A5640),
    onSurfaceVariant = Color(0xFFC9D3C0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
