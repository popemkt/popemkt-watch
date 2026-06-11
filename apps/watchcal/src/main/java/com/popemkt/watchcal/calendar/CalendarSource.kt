package com.popemkt.watchcal.calendar

import com.popemkt.watchcal.domain.EventInstance

/** The boundary the rest of the app depends on; the mirror reader is an implementation detail. */
interface CalendarSource {
    suspend fun instances(beginMillis: Long, endMillis: Long): List<EventInstance>
}
