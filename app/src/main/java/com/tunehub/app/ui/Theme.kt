@file:OptIn(ExperimentalTextApi::class)

package com.tunehub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunehub.app.R

/** The Aurora Glass palette: deep space ink, glowing violet / cyan / pink, frosted glass. */
object Aurora {
    val Ink = Color(0xFF08081A)
    val Violet = Color(0xFF7B4DFF)
    val VioletSoft = Color(0xFF8F6BFF)
    val Cyan = Color(0xFF6FF3FF)
    val Pink = Color(0xFFFF3FB4)
    val Amber = Color(0xFFFFB547)
    val Text = Color(0xFFF4F3FF)
    val Muted = Color(0xFFC9C6E8)
    val Lilac = Color(0xFFB9B2FF)
    val Glass = Color.White.copy(alpha = 0.08f)
    val GlassStrong = Color.White.copy(alpha = 0.13f)
    val Stroke = Color.White.copy(alpha = 0.16f)
    val Sheet = Color(0xFF15122E)

    val accent = Brush.linearGradient(listOf(VioletSoft, Cyan))
    val warm = Brush.linearGradient(listOf(Color(0xFFFF9A3C), Pink, Violet))
}

val Ink = Aurora.Ink

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Outfit = FontFamily(
    variable(R.font.outfit, 400),
    variable(R.font.outfit, 500),
    variable(R.font.outfit, 600),
    variable(R.font.outfit, 700),
    variable(R.font.outfit, 800),
)

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 700),
)

private val colors = darkColorScheme(
    primary = Aurora.Cyan,
    onPrimary = Aurora.Ink,
    secondary = Aurora.Violet,
    onSecondary = Aurora.Text,
    tertiary = Aurora.Pink,
    background = Aurora.Ink,
    onBackground = Aurora.Text,
    surface = Aurora.Ink,
    onSurface = Aurora.Text,
    surfaceVariant = Color(0xFF1E1A40),
    onSurfaceVariant = Aurora.Muted,
    surfaceContainerLowest = Aurora.Ink,
    surfaceContainerLow = Color(0xFF110E28),
    surfaceContainer = Aurora.Sheet,
    surfaceContainerHigh = Color(0xFF1E1A40),
    surfaceContainerHighest = Color(0xFF282352),
    outline = Color(0xFF3B3566),
    outlineVariant = Color(0xFF2A2550),
)

private val type = Typography(
    displaySmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Outfit, fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Outfit, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Outfit, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Outfit, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Outfit, fontSize = 12.5.sp),
    labelLarge = TextStyle(fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Outfit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Outfit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

@Composable
fun TuneHubTheme(content: @Composable () -> Unit) {
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

    val Download = icon("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    val DownloadDone = icon("M5 18h14v2H5v-2zm4.6-2.7L5 10.7l2-1.9 2.6 2.6L17 4l2 2-9.4 9.3z")
    val Lyrics = icon("M14 17H4v2h10v-2zm6-8H4v2h16V9zM4 15h16v-2H4v2zM4 5v2h16V5H4z")
    val Timer = icon(
        "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 " +
            "4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 " +
            "20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"
    )
    val Radio = icon(
        "M12 5c-3.87 0-7 3.13-7 7h2c0-2.76 2.24-5 5-5s5 2.24 5 5h2c0-3.87-3.13-7-7-7zm1 9.29c.88-.39 " +
            "1.5-1.26 1.5-2.29 0-1.38-1.12-2.5-2.5-2.5S9.5 10.62 9.5 12c0 1.02.62 1.9 1.5 2.29v3.3L7.59 " +
            "21 9 22.41l3-3 3 3L16.41 21 13 17.59v-3.3zM12 1C5.93 1 1 5.93 1 12h2c0-4.97 4.03-9 9-9s9 " +
            "4.03 9 9h2c0-6.07-4.93-11-11-11z"
    )
    val Album = icon(
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-" +
            "4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 0-1 .45-1 1s.45 1 1 1 " +
            "1-.45 1-1-.45-1-1-1z"
    )
    val Sort = icon("M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z")
    val Equalizer = icon("M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z")
    val History = icon(
        "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 " +
            "7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-" +
            "9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
    )

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
