package com.spitify.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Lime = Color(0xFFB4F05A)
val Ink = Color(0xFF0E0E10)

private val colors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    secondary = Color(0xFF8FA87A),
    background = Ink,
    onBackground = Color(0xFFF2F2F2),
    surface = Ink,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1F22),
    onSurfaceVariant = Color(0xFFA3A3A8),
    surfaceContainer = Color(0xFF16171A),
    surfaceContainerHigh = Color(0xFF232428),
    surfaceContainerHighest = Color(0xFF2C2D31),
    outline = Color(0xFF3A3B40),
)

private val type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SpitifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

/** Material icons not in material-icons-core (the extended set adds ~10 MB to the APK). */
object AppIcons {
    val Pause = icon("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val SkipNext = icon("M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    val SkipPrevious = icon("M6 6h2v12H6zm3.5 6l8.5 6V6z")
    val Shuffle = icon(
        "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 " +
            "17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"
    )
    val Repeat = icon("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z")
    val RepeatOne = icon(
        "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"
    )
    val QueueMusic = icon(
        "M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 " +
            "1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"
    )
    val Library = icon(
        "M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5c0 " +
            "1.38-1.12 2.5-2.5 2.5S10 13.88 10 12.5s1.12-2.5 2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 " +
            "6H2v14c0 1.1.9 2 2 2h14v-2H4V6z"
    )
    val PlaylistAdd = icon("M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z")
    val MusicNote = icon(
        "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"
    )

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
