package com.popemkt.watchcal.tile

import android.content.Context
import android.text.format.DateFormat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
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
 * Builds the tile's ProtoLayout from a resolved [TileModel]. Pure presentation —
 * no data access, no side effects (specs/01-architecture.md § Tile surface).
 */
object AgendaTileRenderer {

    const val ID_PREV = "prev"
    const val ID_NEXT = "next"
    const val ID_OPEN = "open"

    private const val MAIN_ACTIVITY = "com.popemkt.watchcal.ui.MainActivity"

    private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
    private const val COLOR_MUTED = 0xFF9E9E9E.toInt()
    private const val COLOR_ACCENT = 0xFF4DB6A4.toInt()

    fun render(context: Context, params: DeviceParameters, model: TileModel): LayoutElement =
        when (model) {
            TileModel.NeedsPermission ->
                message(context, params, "Open WatchCal to grant calendar access")
            TileModel.Empty -> message(context, params, "Nothing ahead")
            is TileModel.Card -> card(context, params, model)
        }

    private fun card(context: Context, params: DeviceParameters, model: TileModel.Card): LayoutElement {
        val entry = model.entry
        val done = entry.state is ReminderState.Done
        val titleColor = if (done) COLOR_MUTED else COLOR_ON_SURFACE

        val content = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(caption(context, captionText(context, model)))
            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
            .addContent(
                Text.Builder(context, entry.instance.title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(titleColor))
                    .setMaxLines(MAX_TITLE_LINES)
                    .build(),
            )
            .addContent(caption(context, timeLabel(context, entry)))
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(cycleRow(context, model.total))
            .build()

        return PrimaryLayout.Builder(params)
            .setResponsiveContentInsetEnabled(true)
            .setContent(content)
            .setPrimaryChipContent(openChip(context, params))
            .build()
    }

    private fun cycleRow(context: Context, total: Int): LayoutElement {
        val canCycle = total > 1
        return Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(stepButton(context, "‹", ID_PREV, canCycle))
            .addContent(Spacer.Builder().setWidth(dp(12f)).build())
            .addContent(stepButton(context, "›", ID_NEXT, canCycle))
            .build()
    }

    private fun stepButton(context: Context, glyph: String, id: String, enabled: Boolean): LayoutElement =
        Button.Builder(context, loadClickable(id))
            .setTextContent(if (enabled) glyph else " ")
            .build()

    private fun openChip(context: Context, params: DeviceParameters): LayoutElement =
        CompactChip.Builder(context, "Open", openClickable(), params).build()

    private fun message(context: Context, params: DeviceParameters, text: String): LayoutElement =
        PrimaryLayout.Builder(params)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                Text.Builder(context, text)
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(COLOR_ON_SURFACE))
                    .setMaxLines(MAX_MESSAGE_LINES)
                    .build(),
            )
            .setPrimaryChipContent(openChip(context, params))
            .build()

    private fun caption(context: Context, text: String): LayoutElement =
        Text.Builder(context, text)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_ACCENT))
            .setMaxLines(1)
            .build()

    private fun captionText(context: Context, model: TileModel.Card): String {
        val position = "${model.index + 1} / ${model.total}"
        val day = dayLabel(model.entry.instance.beginMillis)
        val glyph = stateGlyph(model.entry.state)
        return listOfNotNull(day, position, glyph).joinToString("  ·  ")
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
                            .setPackageName("com.popemkt.watchcal")
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
