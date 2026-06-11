package com.popemkt.watchcal.domain

import java.util.concurrent.TimeUnit

object ReminderDefaults {
    val SNOOZE_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
    val AGENDA_WINDOW_MILLIS: Long = TimeUnit.HOURS.toMillis(48)
}
