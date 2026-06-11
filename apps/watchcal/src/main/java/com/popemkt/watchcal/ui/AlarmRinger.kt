package com.popemkt.watchcal.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The audible half of the full-screen alert: looping alarm-stream ringtone +
 * repeating vibration. Lifetime is owned by AlarmActivity (start/stop in
 * onStart/onStop) so ringing can never outlive the visible alert.
 */
class AlarmRinger(private val context: Context) {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun start() {
        ringtone = alarmTone()?.apply { play() }
        vibrator = defaultVibrator().apply {
            vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun alarmTone(): Ringtone? {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return null
        return RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
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
