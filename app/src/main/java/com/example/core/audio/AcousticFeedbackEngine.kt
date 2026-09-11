package com.example.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AcousticFeedbackEngine(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    var isAudioEnabled: Boolean = true
    var isHapticEnabled: Boolean = true

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            Log.w("AcousticFeedback", "Could not initialize ToneGenerator", e)
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w("AcousticFeedback", "Could not initialize Vibrator", e)
        }
    }

    /**
     * Plays an acoustic beep tone modulated by geophysical gradient / signal strength.
     * High positive readings yield high pitch; negative readings yield low tones.
     */
    fun playFeedbackForValue(value: Float, baseline: Float = 0f) {
        if (!isAudioEnabled) return

        val diff = value - baseline
        try {
            when {
                diff > 40f -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 60)
                    triggerHaptic(60)
                }
                diff > 15f -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                    triggerHaptic(30)
                }
                diff < -20f -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
                }
                else -> {
                    // Subtle step tick
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 25)
                }
            }
        } catch (e: Exception) {
            // Ignore transient tone errors
        }
    }

    /**
     * Triggers subtle haptic pulse for gradient spikes.
     */
    fun triggerHaptic(durationMs: Long = 40) {
        if (!isHapticEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
