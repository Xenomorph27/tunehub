package com.tunehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunehub.app.TuneHubApp
import com.tunehub.app.playback.AudioFx
import java.text.NumberFormat

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val library = TuneHubApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val bands by AudioFx.bands.collectAsStateWithLifecycle()
    val eq = data.eq
    val frequencyFormat = remember { NumberFormat.getNumberInstance() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsHeading("Sound", onBack)

        // UltraTune: one tap for the boosted sound, overriding the manual settings below
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .panel(RoundedCornerShape(18.dp), fill = tuneColors.raised)
                .toggleable(value = eq.ultra, role = Role.Switch) { on -> library.updateEq { it.copy(ultra = on) } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("UltraTune", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = eq.ultra,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = tuneColors.accent, checkedThumbColor = tuneColors.onAccent),
                )
            }
            Text(
                "A preset with deeper bass, brighter vocals and a wider soundstage.",
                style = MaterialTheme.typography.bodyMedium,
                color = tuneColors.muted,
            )
            ConsoleMeter(if (eq.ultra) 1f else 0f, Modifier.fillMaxWidth())
        }

        val manualEnabled = !eq.ultra
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .panel(RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleLarge)
                    if (!manualEnabled) {
                        Text("Turn off UltraTune to adjust the EQ.", style = MaterialTheme.typography.bodySmall, color = tuneColors.muted)
                    }
                }
                Switch(
                    checked = eq.enabled,
                    enabled = manualEnabled,
                    onCheckedChange = { on -> library.updateEq { it.copy(enabled = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = tuneColors.accent, checkedThumbColor = tuneColors.onAccent),
                )
            }

            val info = bands
            if (info == null) {
                Text(
                    "Play a song once to load your phone's equalizer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tuneColors.muted,
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
                        ConsoleKey(name, Modifier.semantics { selected = eq.preset == name }, primary = eq.preset == name, enabled = manualEnabled) {
                            library.updateEq { it.copy(preset = name, enabled = true, bands = AudioFx.curve(name, info)) }
                        }
                    }
                }
                info.centersHz.forEachIndexed { i, hz ->
                    val level = eq.bands.getOrElse(i) { 0 }
                    SliderRow(
                        label = if (hz >= 1000) "${frequencyFormat.format(hz / 1000.0)} kHz" else "$hz Hz",
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
                .panel(RoundedCornerShape(18.dp))
                .padding(16.dp),
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
            "Effects apply to TuneHub. Availability varies by device.",
            style = MaterialTheme.typography.bodySmall,
            color = tuneColors.muted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val current = value.coerceIn(range)
    val span = range.endInclusive - range.start
    val fill = if (span > 0f) (current - range.start) / span else 0f
    val zero = if (span > 0f) (0f.coerceIn(range) - range.start) / span else 0f
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 8.dp))
            ConsoleReadout(valueText, Modifier.widthIn(min = 86.dp))
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = valueText
            },
            thumb = {
                Box(Modifier.size(12.dp, 22.dp).background(
                    if (enabled) tuneColors.accentText else tuneColors.track,
                    RoundedCornerShape(3.dp),
                ))
            },
            track = { ConsoleMeter(fill, Modifier.fillMaxWidth(), origin = zero, enabled = enabled) },
        )
    }
}
