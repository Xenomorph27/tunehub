package com.tunehub.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Matte surface with the same hairline used in the bottom dock. */
@Composable
fun Modifier.panel(shape: Shape, fill: Color = tuneColors.surface, stroke: Color = tuneColors.rule): Modifier =
    clip(shape).background(fill).border(1.dp, stroke, shape)

@Composable
fun PaperBackground(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxSize().background(tuneColors.background))

@Composable
fun PanelIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = tuneColors.text,
    fill: Color = tuneColors.surface,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .panel(RoundedCornerShape(12.dp), fill = fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AccentCircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) tuneColors.accent else tuneColors.raised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Icon(icon, null, tint = tuneColors.onAccent, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun FilterPill(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(48.dp)
            .panel(shape, fill = if (selected) tuneColors.accent else tuneColors.surface)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tuneColors.onAccent else tuneColors.text,
        )
    }
}

/** The LCD progress display also handles taps, drags, and accessibility seeks. */
@Composable
fun SegmentedSeekBar(
    songId: String,
    progress: Float,
    modifier: Modifier = Modifier,
    playedColor: Color,
    playheadColor: Color,
    unplayedColor: Color,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float?) -> Unit = {},
) {
    // the gesture handlers live across recompositions: always call the latest callbacks
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnPreview by rememberUpdatedState(onSeekPreview)
    var dragFraction by remember(songId) { mutableStateOf<Float?>(null) }
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
            .pointerInput(songId) {
                detectTapGestures { offset -> currentOnSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(songId) {
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
        val segments = 20
        val filled = (shown * segments).roundToInt().coerceIn(0, segments)
        val gap = minOf(3.dp.toPx(), size.width / (2 * segments))
        val width = (size.width - gap * (segments - 1)) / segments
        val height = 10.dp.toPx()
        repeat(segments) { index ->
            drawRoundRect(
                color = when {
                    index == filled - 1 -> playheadColor
                    index < filled -> playedColor
                    else -> unplayedColor
                },
                topLeft = Offset(index * (width + gap), (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}
