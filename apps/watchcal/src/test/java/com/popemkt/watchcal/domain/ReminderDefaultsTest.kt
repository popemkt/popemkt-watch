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

    @Test
    fun `configured 10 minute snooze seeds the preset cycle`() {
        assertEquals(1, ReminderDefaults.snoozePresetIndexFor(600_000L))
    }

    @Test
    fun `custom snooze interval falls back to 10 minute preset`() {
        assertEquals(1, ReminderDefaults.snoozePresetIndexFor(90_000L))
    }

    @Test
    fun `snooze preset cycle wraps after one hour`() {
        assertEquals(2, ReminderDefaults.nextSnoozePresetIndex(1))
        assertEquals(0, ReminderDefaults.nextSnoozePresetIndex(3))
    }
}
