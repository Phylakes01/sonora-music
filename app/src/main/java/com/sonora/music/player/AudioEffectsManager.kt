@file:Suppress("DEPRECATION")

package com.sonora.music.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.sonora.music.model.AudioSettings
import kotlin.math.roundToInt

class AudioEffectsManager {

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var lastSettings: AudioSettings = AudioSettings()

    fun attachToSession(audioSessionId: Int, settings: AudioSettings) {
        lastSettings = settings
        if (audioSessionId <= 0) {
            releaseEffectsOnly()
            currentSessionId = 0
            return
        }

        if (audioSessionId == currentSessionId && (equalizer != null || bassBoost != null || virtualizer != null)) {
            applySettings(settings)
            return
        }

        releaseEffectsOnly()
        currentSessionId = audioSessionId

        equalizer = try {
            Equalizer(0, audioSessionId)
        } catch (_: Throwable) {
            null
        }

        bassBoost = try {
            BassBoost(0, audioSessionId)
        } catch (_: Throwable) {
            null
        }

        virtualizer = try {
            Virtualizer(0, audioSessionId)
        } catch (_: Throwable) {
            null
        }

        applySettings(settings)
    }

    fun applySettings(settings: AudioSettings) {
        lastSettings = settings

        try {
            equalizer?.let { eq ->
                eq.enabled = settings.eqEnabled
                if (settings.eqEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minLevelMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
                    val maxLevelMb = range.getOrElse(1) { 1500.toShort() }.toInt()
                    val sourceBands = settings.bandLevelsMb.ifEmpty { settings.eqPreset.bandOffsetsMb }

                    for (band in 0 until numBands) {
                        val targetMb = scaleBandLevel(
                            bandIndex = band,
                            numBands = numBands,
                            bandLevelsMb = sourceBands,
                            minLevelMb = minLevelMb,
                            maxLevelMb = maxLevelMb
                        )
                        eq.setBandLevel(band.toShort(), targetMb.toShort())
                    }
                }
            }
        } catch (_: Throwable) {
            // Device/emulator without Equalizer HAL support
        }

        try {
            bassBoost?.let { bb ->
                val enabled = settings.eqEnabled && settings.bassBoostStrength > 0
                bb.enabled = enabled
                if (enabled && bb.strengthSupported) {
                    bb.setStrength(settings.bassBoostStrength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Throwable) {
            // Device/emulator without BassBoost HAL support
        }

        try {
            virtualizer?.let { virt ->
                val enabled = settings.eqEnabled && settings.virtualizerStrength > 0
                virt.enabled = enabled
                if (enabled && virt.strengthSupported) {
                    virt.setStrength(settings.virtualizerStrength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Throwable) {
            // Device/emulator without Virtualizer HAL support
        }
    }

    fun release() {
        releaseEffectsOnly()
        currentSessionId = 0
    }

    private fun releaseEffectsOnly() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        } finally {
            equalizer = null
        }

        try {
            bassBoost?.release()
        } catch (_: Throwable) {
        } finally {
            bassBoost = null
        }

        try {
            virtualizer?.release()
        } catch (_: Throwable) {
        } finally {
            virtualizer = null
        }
    }

    companion object {
        private const val REFERENCE_RANGE_MB = 1500f

        internal fun scaleBandLevel(
            bandIndex: Int,
            numBands: Int,
            bandLevelsMb: List<Int>,
            minLevelMb: Int,
            maxLevelMb: Int
        ): Int {
            if (bandLevelsMb.isEmpty() || numBands <= 0) {
                return 0.coerceIn(minLevelMb, maxLevelMb)
            }

            val rawMb: Float = when {
                bandLevelsMb.size == 1 -> bandLevelsMb[0].toFloat()
                numBands == 1 -> bandLevelsMb.average().toFloat()
                else -> {
                    val pos = bandIndex.toFloat() * (bandLevelsMb.size - 1) / (numBands - 1)
                    val lo = pos.toInt().coerceIn(0, bandLevelsMb.lastIndex)
                    val hi = (lo + 1).coerceAtMost(bandLevelsMb.lastIndex)
                    val frac = pos - lo
                    bandLevelsMb[lo] + (bandLevelsMb[hi] - bandLevelsMb[lo]) * frac
                }
            }

            val scaled = when {
                rawMb >= 0f && maxLevelMb > 0 -> ((rawMb / REFERENCE_RANGE_MB) * maxLevelMb).roundToInt()
                rawMb < 0f && minLevelMb < 0 -> ((-rawMb / REFERENCE_RANGE_MB) * minLevelMb).roundToInt()
                else -> rawMb.roundToInt()
            }
            return scaled.coerceIn(minLevelMb, maxLevelMb)
        }
    }
}
