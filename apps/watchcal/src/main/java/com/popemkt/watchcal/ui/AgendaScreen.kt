package com.popemkt.watchcal.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.popemkt.watchcal.domain.ReminderState
import java.util.Date

@Composable
fun AgendaScreen(entries: List<AgendaEntry>, onMarkDone: (AgendaEntry) -> Unit) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            item { Text("Nothing in the next 48h", style = MaterialTheme.typography.body2) }
        }
        items(entries, key = { it.instance.instanceKey }) { entry ->
            AgendaRow(entry, onMarkDone)
        }
    }
}

@Composable
private fun AgendaRow(entry: AgendaEntry, onMarkDone: (AgendaEntry) -> Unit) {
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(entry.instance.beginMillis))
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text(entry.instance.title, maxLines = 2) },
        secondaryLabel = { Text("$time · ${entry.stateLabel(context)}") },
        onClick = { if (entry.state != ReminderState.Done) onMarkDone(entry) },
    )
}

private fun AgendaEntry.stateLabel(context: android.content.Context): String = when (val s = state) {
    ReminderState.Done -> "done"
    is ReminderState.Snoozed ->
        "snoozed until ${DateFormat.getTimeFormat(context).format(Date(s.untilMillis))}"
    else -> if (instance.allDay) "all day" else "upcoming"
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Text("WatchCal needs calendar access to show your events.") }
        item { Button(onClick = onRequest) { Text("Grant") } }
    }
}
