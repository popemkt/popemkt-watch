package com.popemkt.watchcal.reminders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.popemkt.watchcal.domain.ReminderState
import kotlinx.coroutines.flow.first

private val Context.reminderPrefs: DataStore<Preferences> by preferencesDataStore(name = "reminder_state")

/**
 * Persists per-instance reminder state. One flat entry per instance key
 * (`"st:<key>" -> "done" | "snoozed:<untilMillis>"`); absence = Upcoming.
 * Pruned to the visible window on every refresh so it never grows.
 */
class ReminderStateStore(private val context: Context) {

    suspend fun states(): Map<String, ReminderState> =
        context.reminderPrefs.data.first().asMap().mapNotNull { (prefKey, value) ->
            val instanceKey = prefKey.name.removePrefix(PREFIX)
            if (instanceKey == prefKey.name) return@mapNotNull null
            decode(value as String)?.let { instanceKey to it }
        }.toMap()

    suspend fun markDone(instanceKey: String) = put(instanceKey, VALUE_DONE)

    suspend fun markSnoozed(instanceKey: String, untilMillis: Long) =
        put(instanceKey, "$VALUE_SNOOZED:$untilMillis")

    /** Drops state for instances no longer in the visible window (moved, deleted, scrolled past). */
    suspend fun prune(liveInstanceKeys: Set<String>) {
        context.reminderPrefs.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(PREFIX) && it.name.removePrefix(PREFIX) !in liveInstanceKeys }
                .forEach { prefs.remove(it) }
        }
    }

    private suspend fun put(instanceKey: String, value: String) {
        context.reminderPrefs.edit { it[stringPreferencesKey(PREFIX + instanceKey)] = value }
    }

    private fun decode(value: String): ReminderState? = when {
        value == VALUE_DONE -> ReminderState.Done
        value.startsWith("$VALUE_SNOOZED:") ->
            value.substringAfter(':').toLongOrNull()?.let { ReminderState.Snoozed(it) }
        else -> null
    }

    private companion object {
        const val PREFIX = "st:"
        const val VALUE_DONE = "done"
        const val VALUE_SNOOZED = "snoozed"
    }
}
