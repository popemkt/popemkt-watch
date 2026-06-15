package com.popemkt.watchcal.reminders

import com.popemkt.watchcal.calendar.CalendarSource
import com.popemkt.watchcal.domain.ReminderDefaults
import com.popemkt.watchcal.domain.ReminderPlanner

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
        stateStore.markSnoozed(instanceKey, clock() + settingsStore.snoozeIntervalMillis())
        refresh()
    }

    suspend fun markDone(instanceKey: String) {
        notifier.cancel(instanceKey)
        stateStore.markDone(instanceKey)
        refresh()
    }

    /** Back to Upcoming: fires at event start again, or immediately if start already passed. */
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
