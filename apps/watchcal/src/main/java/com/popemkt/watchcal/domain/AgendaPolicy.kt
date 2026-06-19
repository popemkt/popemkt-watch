package com.popemkt.watchcal.domain

/** Shared agenda semantics used by app and tile presentation surfaces. */
object AgendaPolicy {

    enum class TapAction {
        MarkDone,
        Snooze,
        Reset,
    }

    fun entries(
        instances: List<EventInstance>,
        states: Map<String, ReminderState>,
    ): List<AgendaEntry> = instances.map { AgendaEntry(it, states[it.instanceKey]) }

    fun tapAction(state: ReminderState?): TapAction = when (state) {
        ReminderState.Done -> TapAction.Snooze
        is ReminderState.Snoozed -> TapAction.Reset
        else -> TapAction.MarkDone
    }
}
