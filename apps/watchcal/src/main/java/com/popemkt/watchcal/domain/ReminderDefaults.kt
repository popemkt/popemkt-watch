package com.popemkt.watchcal.domain

import java.util.concurrent.TimeUnit

object ReminderDefaults {
    val SNOOZE_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
    val SNOOZE_INTERVAL_MIN_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)
    val SNOOZE_INTERVAL_MAX_MILLIS: Long = TimeUnit.MINUTES.toMillis(60)

    /** Forward horizon the firing pipeline scans to find the next wake (refresh only). */
    val SCHEDULING_FORWARD_MILLIS: Long = TimeUnit.HOURS.toMillis(48)

    /**
     * An Upcoming reminder rings only within this window after its start. Past it the
     * instance is **missed**: still shown in the agenda (markable done / snoozable),
     * but never auto-rung — this is what stops a cold start from blasting every
     * already-started event of the day (specs/00-product § Reminder lifecycle).
     */
    val MISSED_GRACE_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)

    /** Agenda *display* spans the whole calendar mirror — past included (specs/00-product § Agenda). */
    val AGENDA_LOOKBACK_MILLIS: Long = TimeUnit.DAYS.toMillis(2)
    val AGENDA_FORWARD_MILLIS: Long = TimeUnit.DAYS.toMillis(8)

    /** Ring/vibrate budget of one full-screen alert; expiry auto-snoozes (00-product § Alarm-style alert). */
    val RING_TIMEOUT_MILLIS: Long = TimeUnit.SECONDS.toMillis(60)

    fun clampSnoozeInterval(millis: Long): Long =
        millis.coerceIn(SNOOZE_INTERVAL_MIN_MILLIS, SNOOZE_INTERVAL_MAX_MILLIS)
}
