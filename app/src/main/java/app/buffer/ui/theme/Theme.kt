package app.buffer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF141318)
val Surface = Color(0xFF1D1C22)
val Line = Color(0xFF2C2B33)
val Ink = Color(0xFFF3EFF0)
val Muted = Color(0xFF9B99A6)
val Accent = Color(0xFFC0C1FF)
val OnAccent = Color(0xFF1F1F5C)
val Warn = Color(0xFFFFB4A8)

@Composable
fun BufferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Bg,
            onBackground = Ink,
            surface = Surface,
            onSurface = Ink,
            onSurfaceVariant = Muted,
            outline = Line,
        ),
        content = content,
    )
}
