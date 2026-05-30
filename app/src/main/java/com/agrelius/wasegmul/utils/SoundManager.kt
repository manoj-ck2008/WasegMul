package com.agrelius.wasegmul.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Redesigned Auditory Interface.
 * Moves away from harsh system beeps to soft, high-fidelity tones.
 */
class SoundManager(private val context: Context, private val settingsManager: SettingsManager) {
    private var toneGenerator: ToneGenerator? = null

    private fun getGenerator(): ToneGenerator {
        if (toneGenerator == null) {
            val volume = runBlocking { settingsManager.volume.first() } * 100
            // Clearer harmonics for environmental aesthetic
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, volume.toInt())
        }
        return toneGenerator!!
    }

    fun updateVolume() {
        toneGenerator?.release()
        toneGenerator = null
    }

    /** Soft data-processing pulse */
    fun playAnalyzingPulse() {
        getGenerator().startTone(ToneGenerator.TONE_DTMF_D, 50)
    }

    /** Neural confirmation ping */
    fun playNeuralLock() {
        getGenerator().startTone(ToneGenerator.TONE_DTMF_B, 100)
    }

    /** Pleasant UI feedback for buttons */
    fun playTick() {
        getGenerator().startTone(ToneGenerator.TONE_DTMF_1, 30)
    }

    /** Harmonious success chord */
    fun playSuccess() {
        getGenerator().startTone(ToneGenerator.TONE_DTMF_2, 80)
    }

    /** Soft warning chime */
    fun playWarning() {
        getGenerator().startTone(ToneGenerator.TONE_DTMF_5, 120)
    }

    fun performHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
