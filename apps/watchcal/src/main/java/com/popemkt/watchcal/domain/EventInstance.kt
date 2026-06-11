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
) {
    val instanceKey: String = "$eventId:$beginMillis"
}
