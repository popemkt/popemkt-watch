package com.popemkt.watchcal.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
 * ScalingLazyColumn. All grouping + string formatting is precomputed once per
 * entries-change (see [buildSections]) so scrolling never re-buckets or
 * re-formats on the render path — the body just emits prebuilt rows.
 */
@Composable
fun AgendaScreen(
    entries: List<AgendaEntry>,
    onToggleDone: (AgendaEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onGrantFullScreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sections = remember(entries) { buildSections(entries, context) }
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { SettingsGear(onOpenSettings) }
            if (onGrantFullScreen != null) item { GrantFullScreenChip(onGrantFullScreen) }
            if (sections.isEmpty()) item { EmptyAgenda() }
            sections.forEach { section ->
                item(key = "day:${section.label}") { ListHeader { Text(section.label) } }
                items(section.rows, key = { it.key }) { row -> AgendaRow(row, onToggleDone) }
            }
        }
    }
}

@Composable
private fun AgendaRow(row: RowUi, onToggleDone: (AgendaEntry) -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = if (row.muted) ChipDefaults.childChipColors() else ChipDefaults.secondaryChipColors(),
        icon = { Text(row.glyph, style = MaterialTheme.typography.title3) },
        label = {
            Text(
                row.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (row.muted) TextDecoration.LineThrough else TextDecoration.None,
            )
        },
        secondaryLabel = { Text(row.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = { onToggleDone(row.entry) },
    )
}

@Composable
private fun SettingsGear(onClick: () -> Unit) {
    CompactChip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text("⚙  Settings") },
        onClick = onClick,
    )
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
private fun EmptyAgenda() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("○", style = MaterialTheme.typography.display3)
        Text(
            "Nothing in the mirror",
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

// --- precomputed presentation model (off the render path) ---

private data class RowUi(
    val key: String,
    val title: String,
    val glyph: String,
    val secondary: String,
    val muted: Boolean,
    val entry: AgendaEntry,
)

private data class DaySection(val label: String, val rows: List<RowUi>)

/** Buckets entries by local day and pre-renders every per-row string exactly once. */
private fun buildSections(entries: List<AgendaEntry>, context: Context): List<DaySection> {
    val now = System.currentTimeMillis()
    return entries
        .groupBy { localDayIndex(it.instance.beginMillis) }
        .map { (_, dayEntries) ->
            DaySection(
                label = dayLabel(dayEntries.first().instance.beginMillis, now),
                rows = dayEntries.map { it.toRowUi(context, now) },
            )
        }
}

private fun AgendaEntry.toRowUi(context: Context, now: Long): RowUi {
    val time = DateFormat.getTimeFormat(context).format(Date(instance.beginMillis))
    return when (val s = state) {
        ReminderState.Done -> RowUi(key(), instance.title, "✓", "$time · done → snooze", muted = true, entry = this)
        is ReminderState.Snoozed -> {
            val until = DateFormat.getTimeFormat(context).format(Date(s.untilMillis))
            RowUi(key(), instance.title, "Zz", "snoozed til $until → reset", muted = false, entry = this)
        }
        else -> when {
            instance.allDay -> RowUi(key(), instance.title, "○", "all day", muted = false, entry = this)
            instance.beginMillis < now ->
                RowUi(key(), instance.title, "!", "$time · missed → tap: done", muted = false, entry = this)
            else -> RowUi(key(), instance.title, "○", "$time → tap: done", muted = false, entry = this)
        }
    }
}

private fun AgendaEntry.key(): String = instance.instanceKey

/** Local-calendar day bucket; events sorted soonest-first stay contiguous per day. */
private fun localDayIndex(millis: Long): Int = midnight(millis).let { cal ->
    cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(millis: Long, now: Long): String {
    val days = ((midnight(millis).timeInMillis - midnight(now).timeInMillis) / DAY_MILLIS).toInt()
    return when {
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days == -1 -> "Yesterday"
        days in 2..6 -> DateFormat.format("EEEE", millis).toString()
        else -> DateFormat.format("MMM d", millis).toString()
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
