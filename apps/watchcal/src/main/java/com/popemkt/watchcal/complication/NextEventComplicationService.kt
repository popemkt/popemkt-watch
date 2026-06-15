package com.popemkt.watchcal.complication

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.popemkt.watchcal.App
import com.popemkt.watchcal.domain.EventInstance
import com.popemkt.watchcal.domain.ReminderDefaults
import com.popemkt.watchcal.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Read-only watch-face complication: compact countdown to the next mirrored event. */
class NextEventComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        when {
            request.complicationType != ComplicationType.SHORT_TEXT -> emptyData()
            !hasCalendarPermission() -> shortTextData(text = "Open", title = "Calendar")
            else -> nextEventData()
        }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) shortTextData(text = "12m", title = "Next event") else null

    private suspend fun nextEventData(): ComplicationData {
        val now = System.currentTimeMillis()
        val app = application as App
        val event = app.calendarSource
            .instances(now, now + ReminderDefaults.AGENDA_FORWARD_MILLIS)
            .asSequence()
            .filterNot { it.allDay }
            .filter { it.endMillis > now }
            .minByOrNull { it.beginMillis }
            ?: return shortTextData(text = "None", title = "WatchCal")

        return shortTextData(text = countdown(event, now), title = event.title)
    }

    private fun shortTextData(text: String, title: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = plainText(text),
            contentDescription = plainText("$title, $text"),
        )
            .setTitle(plainText(title))
            .setTapAction(openAgendaIntent())
            .build()

    private fun emptyData(): ComplicationData =
        shortTextData(text = "Open", title = "WatchCal")

    private fun countdown(event: EventInstance, now: Long): String {
        val millisUntilStart = event.beginMillis - now
        if (millisUntilStart <= 0) return "Now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilStart).coerceAtLeast(1)
        if (minutes < MINUTES_PER_HOUR) return "${minutes}m"

        val hours = TimeUnit.MILLISECONDS.toHours(millisUntilStart)
        if (hours < HOURS_PER_DAY) return "${hours}h"

        return "${TimeUnit.MILLISECONDS.toDays(millisUntilStart)}d"
    }

    private fun plainText(text: String): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun openAgendaIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_AGENDA,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REQUEST_OPEN_AGENDA = 2001
        const val MINUTES_PER_HOUR = 60
        const val HOURS_PER_DAY = 24
    }
}
