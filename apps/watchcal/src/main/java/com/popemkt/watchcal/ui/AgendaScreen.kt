package com.popemkt.watchcal.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.popemkt.watchcal.domain.ReminderState
import java.util.Calendar
import java.util.Date

/**
 * The agenda (specs/00-product.md § Agenda): a Wear Scaffold over a
 * ScalingLazyColumn. The body narrates its sections — grant warning, day
 * groups, empty state, settings — at one abstraction level; each section's
 * detail lives in a named helper below.
 */
@Composable
fun AgendaScreen(
    entries: List<AgendaEntry>,
    onToggleDone: (AgendaEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onGrantFullScreen: (() -> Unit)? = null,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Agenda") } }
            if (onGrantFullScreen != null) item { GrantFullScreenChip(onGrantFullScreen) }
            if (entries.isEmpty()) item { EmptyAgenda() } else dayGroups(entries, onToggleDone)
            item { SettingsChip(onOpenSettings) }
        }
    }
}

/** Emits a `ListHeader` per day (Today/Tomorrow/weekday) followed by that day's rows. */
private fun ScalingLazyListScope.dayGroups(
    entries: List<AgendaEntry>,
    onToggleDone: (AgendaEntry) -> Unit,
) {
    val now = System.currentTimeMillis()
    entries.groupBy { localDayIndex(it.instance.beginMillis) }.forEach { (_, dayEntries) ->
        item { ListHeader { Text(dayLabel(dayEntries.first().instance.beginMillis, now)) } }
        items(dayEntries, key = { it.instance.instanceKey }) { entry -> AgendaRow(entry, onToggleDone) }
    }
}

@Composable
private fun AgendaRow(entry: AgendaEntry, onToggleDone: (AgendaEntry) -> Unit) {
    val context = LocalContext.current
    val done = entry.state == ReminderState.Done
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = if (done) ChipDefaults.childChipColors() else ChipDefaults.secondaryChipColors(),
        icon = { Text(entry.glyph(), style = MaterialTheme.typography.title3) },
        label = {
            Text(
                entry.instance.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
        },
        secondaryLabel = { Text(entry.secondaryLabel(context), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = { onToggleDone(entry) },
    )
}

/** Leading state glyph — matches the `Zz`/`✓` vocabulary of the alarm buttons. */
private fun AgendaEntry.glyph(): String = when (state) {
    ReminderState.Done -> "✓"
    is ReminderState.Snoozed -> "Zz"
    else -> "○"
}

/** Time + state, and the name of the next tap's outcome (the one-gesture cycle is self-documenting). */
private fun AgendaEntry.secondaryLabel(context: Context): String {
    val time = DateFormat.getTimeFormat(context).format(Date(instance.beginMillis))
    return when (val s = state) {
        ReminderState.Done -> "$time · done → snooze"
        is ReminderState.Snoozed ->
            "snoozed til ${DateFormat.getTimeFormat(context).format(Date(s.untilMillis))} → reset"
        else -> if (instance.allDay) "all day" else "$time → tap: done"
    }
}

@Composable
private fun GrantFullScreenChip(onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        icon = { Text("⚠", style = MaterialTheme.typography.title3) },
        label = { Text("Allow full-screen alerts", maxLines = 2) },
        onClick = onClick,
    )
}

@Composable
private fun SettingsChip(onClick: () -> Unit) {
    CompactChip(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text("⚙  Settings") },
        onClick = onClick,
    )
}

@Composable
private fun EmptyAgenda() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("○", style = MaterialTheme.typography.display3)
        Text(
            "Nothing in the next 48h",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "WatchCal needs calendar access to show your events.",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(onClick = onRequest) { Text("Grant") }
        }
    }
}

/** Local-calendar day bucket; events sorted soonest-first stay contiguous per day. */
private fun localDayIndex(millis: Long): Int = midnight(millis).let { cal ->
    cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(millis: Long, now: Long): String {
    val days = ((midnight(millis).timeInMillis - midnight(now).timeInMillis) / DAY_MILLIS).toInt()
    return when (days) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> DateFormat.format("EEEE", millis).toString()
    }
}

private fun midnight(millis: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
