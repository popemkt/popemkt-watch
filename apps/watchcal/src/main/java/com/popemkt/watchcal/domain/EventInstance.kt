package com.popemkt.watchcal.domain

/**
 * A single occurrence of a (possibly recurring) calendar event.
 * [instanceKey] is `eventId:beginMillis` — a moved event yields a fresh key,
 * which resets its reminder state by design (specs/00-product.md).
 */
data class EventInstance(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val reminderLeadMinutes: Int? = null,
) {
    val instanceKey: String = "$eventId:$beginMillis"
    val triggerAtMillis: Long = reminderLeadMinutes
        ?.takeIf { it >= 0 }
        ?.let { beginMillis - it * MILLIS_PER_MINUTE }
        ?: beginMillis

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
