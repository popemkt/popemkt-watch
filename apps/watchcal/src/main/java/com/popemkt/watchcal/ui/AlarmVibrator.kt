package com.popemkt.watchcal.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Continuous vibration loop for the full-screen takeover — direct hardware,
 * bypassing notification alert policy (which plays the channel salvo only
 * once; specs/01 § Notifications). Owned by AlarmActivity: start in onStart,
 * stop in onStop/on action; the 60 s auto-snooze bounds it.
 */
class AlarmVibrator(private val context: Context) {

    private var vibrator: Vibrator? = null

    fun start() {
        vibrator = defaultVibrator().apply {
            vibrate(VibrationEffect.createWaveform(PATTERN, 0))
        }
    }

    fun stop() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun defaultVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        /** buzz-buzz pause, repeated forever (repeat index 0) until cancelled. */
        val PATTERN = longArrayOf(0, 600, 300, 600, 700)
    }
}
