package com.popemkt.watchcal.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import androidx.wear.provider.WearableCalendarContract
import com.popemkt.watchcal.domain.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the Wear OS calendar mirror — the OS keeps it in sync with the phone,
 * so this app never does network sync (specs/01-architecture.md § Battery design).
 */
class WearCalendarSource(private val contentResolver: ContentResolver) : CalendarSource {

    override suspend fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> =
        withContext(Dispatchers.IO) {
            val uri = WearableCalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, beginMillis) }
                .also { ContentUris.appendId(it, endMillis) }
                .build()

            val reminderLeads = reminderLeadMinutesByEventId()
            val results = mutableListOf<EventInstance>()
            contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    results += EventInstance(
                        eventId = eventId,
                        title = cursor.getString(1) ?: UNTITLED,
                        beginMillis = cursor.getLong(2),
                        endMillis = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                        reminderLeadMinutes = reminderLeads[eventId],
                    )
                }
            }
            results.sortedBy { it.beginMillis }
        }

    private fun reminderLeadMinutesByEventId(): Map<Long, Int> {
        val results = mutableMapOf<Long, Int>()
        contentResolver.query(WearableCalendarContract.Reminders.CONTENT_URI, REMINDER_PROJECTION, null, null, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val minutes = cursor.getInt(1)
                    if (minutes >= 0) results[eventId] = maxOf(results[eventId] ?: 0, minutes)
                }
            }
        return results
    }

    private companion object {
        const val UNTITLED = "(untitled)"
        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val REMINDER_PROJECTION = arrayOf(
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES,
        )
    }
}
