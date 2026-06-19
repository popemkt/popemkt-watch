package com.popemkt.watchcal.reminders

import com.popemkt.watchcal.calendar.CalendarSource
import com.popemkt.watchcal.domain.AgendaPolicy
import com.popemkt.watchcal.domain.ReminderDefaults
import com.popemkt.watchcal.domain.ReminderPlanner
import com.popemkt.watchcal.domain.ReminderState

/**
 * The single pipeline every wakeup source funnels into
 * (specs/01-architecture.md § Data flow). Idempotent — safe to call from
 * alarms, boot, the periodic worker, notification actions, and app open.
 */
class ReminderCoordinator(
    private val calendarSource: CalendarSource,
    private val stateStore: ReminderStateStore,
    private val settingsStore: ReminderSettingsStore,
    private val notifier: ReminderNotifier,
    private val alarmScheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun refresh() {
        val now = clock()
        stateStore.prune(actionableKeys(now))
        val plan = ReminderPlanner.plan(firingInstances(now), stateStore.states(), now)
        plan.due.forEach(notifier::show)
        alarmScheduler.scheduleNext(plan.nextWakeMillis)
    }

    /**
     * Keys for everything the agenda can still *show and act on* — the prune retention horizon.
     * Pruning over the narrower firing window would orphan state the user just wrote on a missed
     * or far-future event still visible in the agenda (it would silently revert on the next
     * refresh). Retention follows the display window, not the firing window.
     */
    private suspend fun actionableKeys(now: Long): Set<String> =
        calendarSource
            .instances(now - ReminderDefaults.AGENDA_LOOKBACK_MILLIS, now + ReminderDefaults.AGENDA_FORWARD_MILLIS)
            .map { it.instanceKey }
            .toSet()

    /** The near window the planner scans to decide what is due now and when to wake next. */
    private suspend fun firingInstances(now: Long) =
        calendarSource.instances(now - PAST_GRACE_MILLIS, now + ReminderDefaults.SCHEDULING_FORWARD_MILLIS)

    suspend fun snooze(instanceKey: String) {
        notifier.cancel(instanceKey)
        val presetIndex = snoozePresetIndex(stateStore.states()[instanceKey])
        val intervalMillis = ReminderDefaults.SNOOZE_PRESET_MILLIS[presetIndex]
        stateStore.markSnoozed(instanceKey, clock() + intervalMillis, presetIndex)
        refresh()
    }

    private suspend fun snoozePresetIndex(state: ReminderState?): Int {
        if (state is ReminderState.Snoozed && state.presetIndex != null) {
            return ReminderDefaults.nextSnoozePresetIndex(state.presetIndex)
        }
        return ReminderDefaults.snoozePresetIndexFor(settingsStore.snoozeIntervalMillis())
    }

    suspend fun markDone(instanceKey: String) {
        notifier.cancel(instanceKey)
        stateStore.markDone(instanceKey)
        refresh()
    }

    suspend fun applyTapAction(instanceKey: String, action: AgendaPolicy.TapAction) {
        when (action) {
            AgendaPolicy.TapAction.MarkDone -> markDone(instanceKey)
            AgendaPolicy.TapAction.Snooze -> snooze(instanceKey)
            AgendaPolicy.TapAction.Reset -> reset(instanceKey)
        }
    }

    /** Back to Upcoming: fires at the event trigger again, or is missed if the trigger already passed. */
    suspend fun reset(instanceKey: String) {
        notifier.cancel(instanceKey)
        stateStore.clear(instanceKey)
        refresh()
    }

    private companion object {
        /** Keep recently-started events in the window so un-acted reminders survive a refresh. */
        const val PAST_GRACE_MILLIS = 6L * 60 * 60 * 1000
    }
}
