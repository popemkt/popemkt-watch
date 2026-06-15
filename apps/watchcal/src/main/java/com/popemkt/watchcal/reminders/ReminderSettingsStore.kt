package com.popemkt.watchcal.reminders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.popemkt.watchcal.domain.ReminderDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsPrefs: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")

/**
 * Persists user-tunable reminder settings. Read at use time, never cached —
 * the interval seeds the next instance's snooze cycle (specs/01-architecture.md).
 */
class ReminderSettingsStore(private val context: Context) {

    val snoozeIntervalFlow: Flow<Long> = context.settingsPrefs.data
        .map { it[KEY_SNOOZE_INTERVAL] ?: ReminderDefaults.SNOOZE_INTERVAL_MILLIS }

    suspend fun snoozeIntervalMillis(): Long = snoozeIntervalFlow.first()

    suspend fun setSnoozeIntervalMillis(millis: Long) {
        val clamped = ReminderDefaults.clampSnoozeInterval(millis)
        context.settingsPrefs.edit { it[KEY_SNOOZE_INTERVAL] = clamped }
    }

    private companion object {
        val KEY_SNOOZE_INTERVAL = longPreferencesKey("snooze_interval_millis")
    }
}
