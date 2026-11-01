package com.tunehub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sin

/** Frosted-glass surface: a faint white fill with a hairline light edge. */
fun Modifier.glass(shape: Shape, fill: Color = Aurora.Glass, stroke: Color = Aurora.Stroke): Modifier =
    clip(shape).background(fill).border(1.dp, stroke, shape)

/**
 * The deep-space backdrop with three soft glows. [tint] (usually the current song's
 * artwork color) replaces the cyan glow, so the whole app picks up the music's mood.
 * Radial gradients instead of blur: same look, no GPU blur cost, works on every Android.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, tint: Color? = null, intensity: Float = 1f) {
    val third by animateColorAsState(tint ?: Aurora.Cyan, tween(900), label = "auroraTint")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Aurora.Ink)
        fun glow(color: Color, center: Offset, radius: Float, alpha: Float) = drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = alpha * intensity), color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        val w = size.width
        val h = size.height
        glow(Aurora.Violet, Offset(w * 0.05f, h * 0.06f), w * 0.85f, 0.55f)
        glow(third, Offset(w * 1.0f, h * 0.36f), w * 0.75f, 0.38f)
        glow(Aurora.Pink, Offset(w * 0.25f, h * 0.95f), w * 0.8f, 0.30f)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Aurora.Text,
    fill: Color = Aurora.Glass,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .glass(RoundedCornerShape(size * 0.36f), fill = fill)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** The signature violet→cyan circle used for play buttons. */
@Composable
fun GradientCircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = Aurora.accent,
    content: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(Aurora.GlassStrong, Aurora.GlassStrong)))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Icon(icon, null, tint = Aurora.Ink, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean = false,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(38.dp)
            .then(
                if (selected) Modifier.clip(shape).background(Aurora.accent)
                else Modifier.glass(shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null && !selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Aurora.Ink else Aurora.Text,
        )
    }
}

/** Text painted with the accent gradient (the TuneHub wordmark). */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GradientText(text: String, style: TextStyle, brush: Brush = Brush.linearGradient(listOf(Color(0xFFC8B6FF), Aurora.Cyan))) {
    Text(text, style = style.copy(brush = brush))
}

/**
 * Seek bar drawn as a waveform: bar heights are derived from the song id, so every song
 * has its own stable shape. Tap or drag to seek.
 */
@Composable
fun WaveformSeekBar(
    seed: String,
    progress: Float,
    modifier: Modifier = Modifier,
    bars: Int = 46,
    playedBrush: Brush = Brush.horizontalGradient(listOf(Color(0xFFC8B6FF), Aurora.Cyan)),
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float?) -> Unit = {},
) {
    val heights = remember(seed, bars) {
        val h = seed.hashCode()
        List(bars) { i ->
            val wave = 0.55f + 0.3f * sin(i * 0.55f + h % 7) + 0.15f * sin(i * 1.7f + h % 13)
            wave.coerceIn(0.18f, 1f)
        }
    }
    // the gesture handlers live across recompositions: always call the latest callbacks
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnPreview by rememberUpdatedState(onSeekPreview)
    var dragFraction by remember(seed) { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progress

    Canvas(
        modifier
            .semantics {
                contentDescription = "Seek bar"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                stateDescription = "${(progress * 100).toInt()}%"
                setProgress { target ->
                    currentOnSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(seed) {
                detectTapGestures { offset -> currentOnSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(seed) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        dragFraction?.let(currentOnSeek)
                        dragFraction = null
                        currentOnPreview(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        currentOnPreview(null)
                    },
                ) { change, _ ->
                    change.consume()
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    dragFraction = f
                    currentOnPreview(f)
                }
            },
    ) {
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        heights.forEachIndexed { i, hf ->
            val x = i * (barW + gap)
            val barH = max(4.dp.toPx(), size.height * hf)
            val top = (size.height - barH) / 2
            val played = (i + 0.5f) / bars <= shown
            if (played) {
                drawRoundRect(
                    brush = playedBrush,
                    topLeft = Offset(x, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2),
                )
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(x, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2),
                )
            }
        }
    }
}

/** A ring around round artwork that fills with playback progress. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 6.dp) {
    Canvas(modifier) {
        val s = stroke.toPx()
        val arcSize = Size(size.width - s, size.height - s)
        val topLeft = Offset(s / 2, s / 2)
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(s),
        )
        if (progress > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Aurora.Violet, Aurora.Cyan, Aurora.Pink, Aurora.Violet)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
    }
}
