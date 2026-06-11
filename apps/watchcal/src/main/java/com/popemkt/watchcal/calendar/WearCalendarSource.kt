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

            val results = mutableListOf<EventInstance>()
            contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    results += EventInstance(
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1) ?: UNTITLED,
                        beginMillis = cursor.getLong(2),
                        endMillis = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                    )
                }
            }
            results.sortedBy { it.beginMillis }
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
    }
}
