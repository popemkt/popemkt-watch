package com.popemkt.watchcal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaPolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `agenda entries pair each instance with its persisted state`() {
        val first = event(1)
        val second = event(2)
        val states = mapOf(second.instanceKey to ReminderState.Done)

        val entries = AgendaPolicy.entries(listOf(first, second), states)

        assertEquals(listOf(AgendaEntry(first, null), AgendaEntry(second, ReminderState.Done)), entries)
    }

    @Test
    fun `tap action matches the shared agenda and tile cycle`() {
        assertEquals(AgendaPolicy.TapAction.MarkDone, AgendaPolicy.tapAction(null))
        assertEquals(AgendaPolicy.TapAction.MarkDone, AgendaPolicy.tapAction(ReminderState.Upcoming))
        assertEquals(AgendaPolicy.TapAction.Snooze, AgendaPolicy.tapAction(ReminderState.Done))
        assertEquals(AgendaPolicy.TapAction.Reset, AgendaPolicy.tapAction(ReminderState.Snoozed(now)))
    }

    private fun event(id: Long) = EventInstance(
        eventId = id,
        title = "event-$id",
        beginMillis = now,
        endMillis = now + 30 * 60_000L,
        allDay = false,
    )
}
