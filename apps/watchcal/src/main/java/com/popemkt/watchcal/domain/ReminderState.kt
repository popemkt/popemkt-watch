package com.popemkt.watchcal.domain

/** Reminder lifecycle per event instance. Absence in the store means [Upcoming]. */
sealed interface ReminderState {
    data object Upcoming : ReminderState
    data class Snoozed(val untilMillis: Long) : ReminderState
    data object Done : ReminderState
}
