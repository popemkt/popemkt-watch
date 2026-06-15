package com.popemkt.watchcal.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ChildButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.popemkt.watchcal.domain.AgendaEntry
import com.popemkt.watchcal.domain.ReminderState
import java.util.Calendar
import java.util.Date

/**
 * The agenda (specs/00-product.md § Agenda): a Material 3 [TransformingLazyColumn] so rows
 * scale + morph to the round display's curve at the top and bottom edges
 * (specs/01-architecture.md § UI rendering). All grouping + string formatting is precomputed
 * once per entries-change (see [buildSections]) so scrolling never re-buckets or re-formats on
 * the render path — the body just emits prebuilt rows. The per-item morph modifier and
 * [SurfaceTransformation] are built at the call site, where the item scope (`this`) is in scope.
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
    val columnState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item(key = "settings") {
                SettingsGear(Modifier.fillMaxWidth().transformedHeight(this, spec), SurfaceTransformation(spec), onOpenSettings)
            }
            if (onGrantFullScreen != null) item(key = "grant") {
                GrantFullScreenChip(Modifier.fillMaxWidth().transformedHeight(this, spec), SurfaceTransformation(spec), onGrantFullScreen)
            }
            if (sections.isEmpty()) item(key = "empty") { EmptyAgenda() }
            sections.forEach { section ->
                item(key = "day:${section.label}") {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) { Text(section.label) }
                }
                items(section.rows, key = { it.key }) { row ->
                    AgendaRow(
                        Modifier.fillMaxWidth().transformedHeight(this, spec),
                        SurfaceTransformation(spec),
                        row,
                        onToggleDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    modifier: Modifier,
    transformation: SurfaceTransformation,
    row: RowUi,
    onToggleDone: (AgendaEntry) -> Unit,
) {
    val label: @Composable RowScope.() -> Unit = {
        Text(
            row.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (row.muted) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
    val secondary: @Composable RowScope.() -> Unit = { Text(row.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    val glyph: @Composable BoxScope.() -> Unit = { Text(row.glyph, style = MaterialTheme.typography.titleMedium) }
    val onClick = { onToggleDone(row.entry) }

    // Done rows recede to a backgroundless ChildButton; active rows are tonal pills.
    if (row.muted) {
        ChildButton(
            onClick = onClick, modifier = modifier, transformation = transformation,
            label = label, secondaryLabel = secondary, icon = glyph,
        )
    } else {
        FilledTonalButton(
            onClick = onClick, modifier = modifier, transformation = transformation,
            label = label, secondaryLabel = secondary, icon = glyph,
        )
    }
}

@Composable
private fun SettingsGear(modifier: Modifier, transformation: SurfaceTransformation, onClick: () -> Unit) {
    ChildButton(
        onClick = onClick, modifier = modifier, transformation = transformation,
        label = { Text("Settings") },
        icon = { Text("⚙", style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
private fun GrantFullScreenChip(modifier: Modifier, transformation: SurfaceTransformation, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, modifier = modifier, transformation = transformation,
        label = { Text("Allow full-screen alerts", maxLines = 2) },
        icon = { Text("⚠", style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
private fun EmptyAgenda() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("○", style = MaterialTheme.typography.displaySmall)
        Text(
            "Nothing in the mirror",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "WatchCal needs calendar access to show your events.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(onClick = onRequest, label = { Text("Grant") })
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
