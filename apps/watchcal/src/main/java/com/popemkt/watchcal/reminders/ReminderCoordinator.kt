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
    private val notifier: ReminderNotifier,
    private val alarmScheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun refresh() {
        val now = clock()
        val instances = calendarSource.instances(now - PAST_GRACE_MILLIS, now + ReminderDefaults.AGENDA_WINDOW_MILLIS)
        stateStore.prune(instances.map { it.instanceKey }.toSet())
        val plan = ReminderPlanner.plan(instances, stateStore.states(), now)
        plan.due.forEach(notifier::show)
        alarmScheduler.scheduleNext(plan.nextWakeMillis)
    }

    suspend fun snooze(instanceKey: String) {
        notifier.cancel(instanceKey)
        stateStore.markSnoozed(instanceKey, clock() + ReminderDefaults.SNOOZE_INTERVAL_MILLIS)
        refresh()
    }

    suspend fun markDone(instanceKey: String) {
        notifier.cancel(instanceKey)
        stateStore.markDone(instanceKey)
        refresh()
    }

    private companion object {
        /** Keep recently-started events in the window so un-acted reminders survive a refresh. */
        const val PAST_GRACE_MILLIS = 6L * 60 * 60 * 1000
    }
}
