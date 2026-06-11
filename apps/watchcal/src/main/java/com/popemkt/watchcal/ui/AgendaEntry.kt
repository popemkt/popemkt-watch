package com.popemkt.watchcal.ui

import com.popemkt.watchcal.domain.EventInstance
import com.popemkt.watchcal.domain.ReminderState

data class AgendaEntry(
    val instance: EventInstance,
    /** null = Upcoming (absence in the store). */
    val state: ReminderState?,
)
