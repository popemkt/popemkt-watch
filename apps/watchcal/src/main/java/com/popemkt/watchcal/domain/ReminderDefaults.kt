package com.popemkt.watchcal.domain

import java.util.concurrent.TimeUnit

object ReminderDefaults {
    val SNOOZE_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
    val SNOOZE_INTERVAL_MIN_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)
    val SNOOZE_INTERVAL_MAX_MILLIS: Long = TimeUnit.MINUTES.toMillis(60)
    val AGENDA_WINDOW_MILLIS: Long = TimeUnit.HOURS.toMillis(48)

    /** Ring/vibrate budget of one full-screen alert; expiry auto-snoozes (00-product § Alarm-style alert). */
    val RING_TIMEOUT_MILLIS: Long = TimeUnit.SECONDS.toMillis(60)

    fun clampSnoozeInterval(millis: Long): Long =
        millis.coerceIn(SNOOZE_INTERVAL_MIN_MILLIS, SNOOZE_INTERVAL_MAX_MILLIS)
}
