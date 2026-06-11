package com.popemkt.watchcal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDefaultsTest {

    @Test
    fun `snooze interval cannot be configured below 10 seconds`() {
        assertEquals(10_000L, ReminderDefaults.clampSnoozeInterval(0L))
        assertEquals(10_000L, ReminderDefaults.clampSnoozeInterval(-5_000L))
    }

    @Test
    fun `snooze interval cannot be configured above one hour`() {
        assertEquals(3_600_000L, ReminderDefaults.clampSnoozeInterval(2 * 3_600_000L))
    }

    @Test
    fun `a sane interval passes through unchanged`() {
        assertEquals(90_000L, ReminderDefaults.clampSnoozeInterval(90_000L))
    }
}
