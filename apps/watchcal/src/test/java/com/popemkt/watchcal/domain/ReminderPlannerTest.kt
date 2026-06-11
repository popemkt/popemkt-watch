package com.popemkt.watchcal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val now = 1_000_000_000_000L
    private val minute = 60_000L

    private fun event(id: Long, beginOffsetMinutes: Long, allDay: Boolean = false) = EventInstance(
        eventId = id,
        title = "event-$id",
        beginMillis = now + beginOffsetMinutes * minute,
        endMillis = now + (beginOffsetMinutes + 30) * minute,
        allDay = allDay,
    )

    @Test
    fun `reminder fires at event start when never snoozed`() {
        val started = event(1, beginOffsetMinutes = 0)
        val plan = ReminderPlanner.plan(listOf(started), emptyMap(), now)
        assertEquals(listOf(started), plan.due)
    }

    @Test
    fun `snoozed reminder keeps coming back until marked done`() {
        val started = event(1, beginOffsetMinutes = -20)
        val snoozedUntil = now + 10 * minute
        val states = mapOf(started.instanceKey to ReminderState.Snoozed(snoozedUntil))

        val whileSnoozed = ReminderPlanner.plan(listOf(started), states, now)
        assertTrue("snoozed instance must not be due yet", whileSnoozed.due.isEmpty())
        assertEquals("the snooze IS the next wake", snoozedUntil, whileSnoozed.nextWakeMillis)

        val afterSnooze = ReminderPlanner.plan(listOf(started), states, snoozedUntil + 1)
        assertEquals("it comes back", listOf(started), afterSnooze.due)
    }

    @Test
    fun `done event never notifies again even after resync`() {
        val started = event(1, beginOffsetMinutes = -20)
        val states = mapOf(started.instanceKey to ReminderState.Done)
        val plan = ReminderPlanner.plan(listOf(started), states, now)
        assertTrue(plan.due.isEmpty())
        assertNull(plan.nextWakeMillis)
    }

    @Test
    fun `planner schedules exactly one next wake at the earliest pending trigger`() {
        val inTen = event(1, beginOffsetMinutes = 10)
        val inThirty = event(2, beginOffsetMinutes = 30)
        val snoozedToFive = event(3, beginOffsetMinutes = -60)
        val states = mapOf(snoozedToFive.instanceKey to ReminderState.Snoozed(now + 5 * minute))

        val plan = ReminderPlanner.plan(listOf(inTen, inThirty, snoozedToFive), states, now)
        assertEquals(now + 5 * minute, plan.nextWakeMillis)
    }

    @Test
    fun `all-day events appear in agenda but never notify`() {
        val allDay = event(1, beginOffsetMinutes = -5, allDay = true)
        val plan = ReminderPlanner.plan(listOf(allDay), emptyMap(), now)
        assertTrue(plan.due.isEmpty())
        assertNull(plan.nextWakeMillis)
    }

    @Test
    fun `a moved event resets - fresh instance key means a fresh upcoming reminder`() {
        val original = event(1, beginOffsetMinutes = -20)
        val moved = event(1, beginOffsetMinutes = 15) // same eventId, new begin
        // State stored under the OLD key no longer matches anything after the move+prune.
        val staleStates = mapOf(original.instanceKey to ReminderState.Done)

        val plan = ReminderPlanner.plan(listOf(moved), staleStates, now)
        assertEquals(now + 15 * minute, plan.nextWakeMillis)
    }
}
