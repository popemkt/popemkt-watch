package com.popemkt.watchcal.tile

import android.content.Context
import android.text.format.DateFormat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.material3.CardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.types.LayoutString
import com.popemkt.watchcal.domain.AgendaEntry
import com.popemkt.watchcal.domain.ReminderState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What the tile shows on a single request — already resolved by the service. */
sealed interface TileModel {
    /** Calendar access not granted — the whole tile taps through to the app to grant. */
    data object NeedsPermission : TileModel

    /** Nothing ahead in the mirror. */
    data object Empty : TileModel

    /** One event in focus out of [total], at zero-based [index]. */
    data class Card(val entry: AgendaEntry, val index: Int, val total: Int) : TileModel
}

/**
 * Builds the tile's ProtoLayout from a resolved [TileModel] using Material 3
 * (`materialScope { primaryLayout(...) }`), so the tile shares the app's M3 look
 * (specs/01-architecture.md § Tile surface). Pure presentation — no data access, no side effects.
 *
 * Layout: a position/time caption in the title slot, a tappable `titleCard` (title + next-tap
 * hint) that cycles the reminder state plus a `‹ ›` nav row in the main slot, and an `Open`
 * `textEdgeButton` hugging the round bottom edge.
 */
object AgendaTileRenderer {

    const val ID_PREV = "prev"
    const val ID_NEXT = "next"
    const val ID_TOGGLE = "toggle"
    const val ID_OPEN = "open"

    private const val PACKAGE = "com.popemkt.watchcal"
    private const val MAIN_ACTIVITY = "com.popemkt.watchcal.ui.MainActivity"

    fun render(context: Context, params: DeviceParameters, model: TileModel): LayoutElement =
        materialScope(context, params) {
            when (model) {
                TileModel.NeedsPermission -> messageLayout("Open WatchCal to grant calendar access")
                TileModel.Empty -> messageLayout("Nothing ahead")
                is TileModel.Card -> cardLayout(context, model)
            }
        }

    private fun MaterialScope.messageLayout(msg: String): LayoutElement =
        primaryLayout(
            mainSlot = { text(LayoutString(msg), maxLines = MAX_MESSAGE_LINES) },
            bottomSlot = { openEdgeButton() },
        )

    private fun MaterialScope.cardLayout(context: Context, model: TileModel.Card): LayoutElement =
        primaryLayout(
            titleSlot = { text(LayoutString(captionText(context, model)), typography = Typography.LABEL_SMALL) },
            mainSlot = { eventColumn(model) },
            bottomSlot = { openEdgeButton() },
        )

    private fun MaterialScope.eventColumn(model: TileModel.Card): LayoutElement {
        val column = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(eventCard(model.entry))
        if (model.total > 1) {
            column.addContent(Spacer.Builder().setHeight(dp(6f)).build())
            column.addContent(navRow())
        }
        return column.build()
    }

    private fun MaterialScope.eventCard(entry: AgendaEntry): LayoutElement =
        titleCard(
            onClick = loadClickable(ID_TOGGLE),
            title = { text(LayoutString(entry.instance.title), maxLines = MAX_TITLE_LINES) },
            content = { text(LayoutString(nextTapHint(entry.state))) },
            colors = eventCardColors(entry.state),
        )

    private fun MaterialScope.navRow(): LayoutElement =
        Row.Builder()
            .addContent(textButton(loadClickable(ID_PREV), { text(LayoutString("‹")) }))
            .addContent(Spacer.Builder().setWidth(dp(12f)).build())
            .addContent(textButton(loadClickable(ID_NEXT), { text(LayoutString("›")) }))
            .build()

    private fun MaterialScope.openEdgeButton(): LayoutElement =
        textEdgeButton(openClickable()) { text(LayoutString("Open")) }

    private fun MaterialScope.eventCardColors(state: ReminderState?): CardColors {
        val background = when (state) {
            ReminderState.Done -> colorScheme.secondaryContainer
            is ReminderState.Snoozed -> colorScheme.tertiaryContainer
            else -> colorScheme.primaryContainer
        }
        val foreground = when (state) {
            ReminderState.Done -> colorScheme.onSecondaryContainer
            is ReminderState.Snoozed -> colorScheme.onTertiaryContainer
            else -> colorScheme.onPrimaryContainer
        }
        return CardColors().copy(
            backgroundColor = background,
            titleColor = foreground,
            contentColor = foreground,
            timeColor = foreground,
            labelColor = foreground,
            secondaryIconColor = foreground,
            secondaryTextColor = foreground,
            graphicIconColor = foreground,
        )
    }

    private fun captionText(context: Context, model: TileModel.Card): String {
        val day = dayLabel(model.entry.instance.beginMillis)
        val time = timeLabel(context, model.entry)
        val position = "${model.index + 1} / ${model.total}"
        val glyph = stateGlyph(model.entry.state)
        return listOfNotNull(day, time, position, glyph).joinToString("  ·  ")
    }

    /** Names the outcome of the next card tap — parity with the agenda row's state line. */
    private fun nextTapHint(state: ReminderState?): String = when (state) {
        ReminderState.Done -> "done → snooze"
        is ReminderState.Snoozed -> "snoozed → reset"
        else -> "tap: done"
    }

    private fun stateGlyph(state: ReminderState?): String? = when (state) {
        ReminderState.Done -> "✓"
        is ReminderState.Snoozed -> "Zz"
        else -> null
    }

    private fun loadClickable(id: String): Clickable =
        Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

    private fun openClickable(): Clickable =
        Clickable.Builder()
            .setId(ID_OPEN)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(PACKAGE)
                            .setClassName(MAIN_ACTIVITY)
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun timeLabel(context: Context, entry: AgendaEntry): String {
        if (entry.instance.allDay) return "all day"
        return DateFormat.getTimeFormat(context).format(Date(entry.instance.beginMillis))
    }

    private fun dayLabel(beginMillis: Long): String {
        val days = dayDelta(beginMillis)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days in 2L..6L -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(beginMillis))
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(beginMillis))
        }
    }

    /** Whole-day difference between [beginMillis] and now, in the device's local time. */
    private fun dayDelta(beginMillis: Long): Long {
        val start = midnight(System.currentTimeMillis())
        val target = midnight(beginMillis)
        return (target - start) / DAY_MILLIS
    }

    private fun midnight(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val MAX_TITLE_LINES = 2
    private const val MAX_MESSAGE_LINES = 3
}
