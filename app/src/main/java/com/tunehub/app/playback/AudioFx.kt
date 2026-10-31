package com.tunehub.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.tunehub.app.data.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ln

/**
 * Equalizer, bass boost, 3D (virtualizer) and loudness, attached to the player's audio
 * session. Every effect is optional: some phones don't support all of them.
 */
object AudioFx {

    /** What the equalizer screen needs to draw its sliders. Empty until the player starts. */
    class BandInfo(val centersHz: List<Int>, val minMb: Int, val maxMb: Int)

    private val _bands = MutableStateFlow<BandInfo?>(null)
    val bands: StateFlow<BandInfo?> = _bands

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var current = EqSettings()

    const val PRESET_ULTRA = "UltraTune"
    val presets = listOf("Flat", "Bass boost", "Vocal", "Treble", "Rock", "Pop", "Acoustic")

    @Synchronized
    fun attach(sessionId: Int) {
        release()
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        _bands.value = eq?.let { e ->
            val range = e.bandLevelRange
            BandInfo(
                centersHz = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
                minMb = range[0].toInt(),
                maxMb = range[1].toInt(),
            )
        }
        apply(current)
    }

    @Synchronized
    fun release() {
        listOfNotNull(eq, bass, virtualizer, loudness).forEach { runCatching { it.release() } }
        eq = null
        bass = null
        virtualizer = null
        loudness = null
    }

    @Synchronized
    fun apply(settings: EqSettings) {
        current = settings
        val on = settings.enabled || settings.ultra
        val info = _bands.value
        runCatching {
            eq?.let { e ->
                val levels = when {
                    settings.ultra && info != null -> curve(PRESET_ULTRA, info)
                    else -> settings.bands
                }
                for (i in 0 until e.numberOfBands) {
                    val level = levels.getOrElse(i) { 0 }.coerceIn(info?.minMb ?: -1500, info?.maxMb ?: 1500)
                    e.setBandLevel(i.toShort(), level.toShort())
                }
                e.enabled = on
            }
        }
        val bassStrength = if (settings.ultra) ULTRA_BASS else settings.bass
        val virtStrength = if (settings.ultra) ULTRA_VIRTUALIZER else settings.virtualizer
        val gainMb = if (settings.ultra) ULTRA_LOUDNESS else settings.loudness
        runCatching {
            bass?.let {
                if (it.strengthSupported) it.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                it.enabled = on && bassStrength > 0
            }
        }
        runCatching {
            virtualizer?.let {
                if (it.strengthSupported) it.setStrength(virtStrength.coerceIn(0, 1000).toShort())
                it.enabled = on && virtStrength > 0
            }
        }
        runCatching {
            loudness?.let {
                it.setTargetGain(gainMb.coerceIn(0, 2000))
                it.enabled = on && gainMb > 0
            }
        }
    }

    /**
     * Band levels (millibels) for a named preset, shaped by each band's frequency so it works
     * whatever bands the phone's equalizer has.
     */
    fun curve(preset: String, info: BandInfo): List<Int> = info.centersHz.map { hz ->
        val db = when (preset) {
            // deep bass, clear vocals and crisp highs, with the low mids pulled back so it stays clean
            PRESET_ULTRA -> shape(hz, bass = 9.0, lowMid = -1.5, mid = 1.0, presence = 5.0, treble = 7.0)
            "Bass boost" -> shape(hz, bass = 8.0, lowMid = 3.0, mid = 0.0, presence = 0.0, treble = 0.0)
            "Vocal" -> shape(hz, bass = -2.0, lowMid = 0.0, mid = 4.0, presence = 5.0, treble = 2.0)
            "Treble" -> shape(hz, bass = 0.0, lowMid = 0.0, mid = 0.0, presence = 4.0, treble = 8.0)
            "Rock" -> shape(hz, bass = 5.0, lowMid = -1.0, mid = -2.0, presence = 3.0, treble = 5.0)
            "Pop" -> shape(hz, bass = 2.0, lowMid = 3.0, mid = 4.0, presence = 2.0, treble = 1.0)
            "Acoustic" -> shape(hz, bass = 3.0, lowMid = 1.0, mid = 2.0, presence = 3.0, treble = 3.0)
            else -> 0.0
        }
        (db * 100).toInt().coerceIn(info.minMb, info.maxMb)
    }

    // picks the gain for a band from where its centre frequency sits on a log scale
    private fun shape(hz: Int, bass: Double, lowMid: Double, mid: Double, presence: Double, treble: Double): Double {
        val x = ln(hz.coerceAtLeast(20).toDouble())
        return when {
            x < ln(150.0) -> bass
            x < ln(500.0) -> lowMid
            x < ln(2000.0) -> mid
            x < ln(6000.0) -> presence
            else -> treble
        }
    }

    private const val ULTRA_BASS = 850
    private const val ULTRA_VIRTUALIZER = 700
    private const val ULTRA_LOUDNESS = 450
}
