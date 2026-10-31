package com.tunehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunehub.app.TuneHubApp
import com.tunehub.app.playback.AudioFx

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val library = TuneHubApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val bands by AudioFx.bands.collectAsStateWithLifecycle()
    val eq = data.eq

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 20.dp)) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text("Sound", style = MaterialTheme.typography.headlineMedium, modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp))
        }

        // UltraTune: one tap for the boosted sound, overriding the manual settings below
        Row(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), fill = Aurora.GlassStrong)
                .background(
                    if (eq.ultra) Brush.linearGradient(listOf(Aurora.Violet.copy(alpha = 0.55f), Aurora.Cyan.copy(alpha = 0.35f)))
                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .clickable { library.updateEq { it.copy(ultra = !it.ultra) } }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                GradientText("UltraTune", MaterialTheme.typography.headlineSmall)
                Text(
                    "Deep bass, clear vocals, crisp highs, wide 3D stage and extra loudness, tuned to stay clean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                )
            }
            Switch(
                checked = eq.ultra,
                onCheckedChange = { on -> library.updateEq { it.copy(ultra = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Aurora.Cyan, checkedThumbColor = Aurora.Ink),
            )
        }

        val manualEnabled = !eq.ultra
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (manualEnabled) "Shape the sound yourself" else "UltraTune is on: turn it off to use these",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Muted,
                    )
                }
                Switch(
                    checked = eq.enabled,
                    enabled = manualEnabled,
                    onCheckedChange = { on -> library.updateEq { it.copy(enabled = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Aurora.Cyan, checkedThumbColor = Aurora.Ink),
                )
            }

            val info = bands
            if (info == null) {
                Text(
                    "Play a song once to load your phone's equalizer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AudioFx.presets.forEach { name ->
                        GlassChip(name, selected = eq.preset == name) {
                            library.updateEq { it.copy(preset = name, enabled = true, bands = AudioFx.curve(name, info)) }
                        }
                    }
                }
                info.centersHz.forEachIndexed { i, hz ->
                    val level = eq.bands.getOrElse(i) { 0 }
                    SliderRow(
                        label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                        value = level.toFloat(),
                        range = info.minMb.toFloat()..info.maxMb.toFloat(),
                        valueText = "%+.1f dB".format(level / 100f),
                        enabled = manualEnabled && eq.enabled,
                    ) { v ->
                        library.updateEq { s ->
                            val levels = List(info.centersHz.size) { j -> s.bands.getOrElse(j) { 0 } }.toMutableList()
                            levels[i] = v.toInt()
                            s.copy(bands = levels, preset = "Custom")
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Text("Effects", style = MaterialTheme.typography.titleLarge)
            val on = manualEnabled && eq.enabled
            SliderRow("Bass boost", eq.bass.toFloat(), 0f..1000f, "${eq.bass / 10}%", on) { v ->
                library.updateEq { it.copy(bass = v.toInt()) }
            }
            SliderRow("3D sound", eq.virtualizer.toFloat(), 0f..1000f, "${eq.virtualizer / 10}%", on) { v ->
                library.updateEq { it.copy(virtualizer = v.toInt()) }
            }
            SliderRow("Loudness", eq.loudness.toFloat(), 0f..1500f, "+%.1f dB".format(eq.loudness / 100f), on) { v ->
                library.updateEq { it.copy(loudness = v.toInt()) }
            }
        }
        Text(
            "Effects apply to TuneHub only. Some phones don't support every effect; unsupported ones do nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = Aurora.Muted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = Aurora.Lilac, modifier = Modifier.width(76.dp))
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Aurora.Cyan,
                activeTrackColor = Aurora.Cyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}
