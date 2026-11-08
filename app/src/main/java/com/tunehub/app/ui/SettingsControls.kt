package com.tunehub.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

@Composable
internal fun SettingsHeading(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).padding(start = 14.dp).semantics { heading() },
        )
    }
}

@Composable
internal fun ConsoleReadout(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .panel(RoundedCornerShape(5.dp), fill = TuneHubInk, stroke = TuneHubAmber.copy(alpha = 0.24f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = TuneHubAmber,
        textAlign = TextAlign.End,
    )
}

@Composable
internal fun ConsoleKey(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val size = modifier.defaultMinSize(minHeight = 48.dp)
    val padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    if (primary) {
        Button(
            onClick, size, enabled, shape,
            colors = ButtonDefaults.buttonColors(containerColor = tuneColors.accent, contentColor = tuneColors.onAccent),
            contentPadding = padding,
        ) { Text(text, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(onClick, size, enabled, shape, contentPadding = padding) {
            Text(text, color = if (enabled) tuneColors.text else tuneColors.muted, textAlign = TextAlign.Center)
        }
    }
}

/** The visual track for the console sliders and the playlist import meter. */
@Composable
internal fun ConsoleMeter(
    progress: Float,
    modifier: Modifier = Modifier,
    origin: Float = 0f,
    enabled: Boolean = true,
) {
    val active = if (enabled) TuneHubAmber else tuneColors.track
    val idle = tuneColors.track.copy(alpha = 0.32f)
    Canvas(modifier.height(8.dp)) {
        val count = 18
        val gap = minOf(3.dp.toPx(), size.width / (count * 2))
        val width = (size.width - gap * (count - 1)) / count
        val start = (origin.coerceIn(0f, 1f) * count).roundToInt()
        val end = (progress.coerceIn(0f, 1f) * count).roundToInt()
        val lit = minOf(start, end) until maxOf(start, end)
        repeat(count) { index ->
            val slot = if (layoutDirection == LayoutDirection.Rtl) count - index - 1 else index
            drawRoundRect(
                color = if (index in lit) active else idle,
                topLeft = Offset(slot * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun HistoryDialog(request: ConfirmRequest?, onDismiss: () -> Unit) {
    request ?: return
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().panel(RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(request.title, style = MaterialTheme.typography.headlineSmall, color = tuneColors.text)
            Text(request.message, style = MaterialTheme.typography.bodyMedium, color = tuneColors.muted)
            HorizontalDivider(color = tuneColors.rule)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConsoleKey("Cancel", Modifier.weight(1f), onClick = onDismiss)
                ConsoleKey(request.confirmLabel, Modifier.weight(1.3f), primary = true) {
                    request.action()
                    onDismiss()
                }
            }
        }
    }
}
