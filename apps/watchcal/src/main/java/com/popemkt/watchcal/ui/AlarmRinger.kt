package com.popemkt.watchcal.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.popemkt.watchcal.R

/**
 * The audible half of the full-screen alert: the bundled alarm tone (looping,
 * alarm stream, player volume maxed — stream volume stays the user's setting)
 * + repeating vibration. Lifetime is owned by AlarmActivity (start/stop in
 * onStart/onStop) so ringing can never outlive the visible alert.
 */
class AlarmRinger(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        player = alarmPlayer()?.apply { start() }
        vibrator = defaultVibrator().apply {
            vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
        }
    }

    fun stop() {
        player?.run {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun alarmPlayer(): MediaPlayer? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // create() returns null if the resource cannot be opened — vibration still alerts.
        return MediaPlayer.create(context, R.raw.watchcal_alarm, attributes, 0)?.apply {
            isLooping = true
            setVolume(1f, 1f)
        }
    }

    private fun defaultVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        /** buzz 600ms, pause 400ms, repeat — alarm-like, distinct from a single notification tap. */
        val VIBRATION_PATTERN = longArrayOf(0, 600, 400)
    }
}
