package com.popemkt.watchcal.domain

/**
 * The entire reminder semantics of specs/00-product.md as a pure function.
 * Given the visible instances, their stored states, and the clock, decide
 * what is due right now and when the single next wake must happen.
 */
object ReminderPlanner {

    data class Plan(
        /** Instances whose notification should be showing right now. */
        val due: List<EventInstance>,
        /** Earliest future trigger, or null when nothing is pending. */
        val nextWakeMillis: Long?,
    )

    fun plan(
        instances: List<EventInstance>,
        states: Map<String, ReminderState>,
        nowMillis: Long,
    ): Plan {
        val pending = instances
            .filterNot { it.allDay }
            .mapNotNull { instance ->
                when (val state = states[instance.instanceKey] ?: ReminderState.Upcoming) {
                    ReminderState.Done -> null
                    // A start-trigger is "live" only near its time; a snooze-return always fires.
                    ReminderState.Upcoming -> Trigger(instance, instance.beginMillis, fromStart = true)
                    is ReminderState.Snoozed -> Trigger(instance, state.untilMillis, fromStart = false)
                }
            }

        val due = pending
            .filter { it.isLiveAt(nowMillis) }
            .sortedBy { it.triggerAt }
            .map { it.instance }
        val nextWakeMillis = pending
            .map { it.triggerAt }
            .filter { it > nowMillis }
            .minOrNull()

        return Plan(due, nextWakeMillis)
    }

    private data class Trigger(val instance: EventInstance, val triggerAt: Long, val fromStart: Boolean) {
        /**
         * Reached, and either a snooze-return (always rings) or a start-trigger still
         * inside the missed grace. A start-trigger older than the grace is *missed* —
         * never rung, only shown in the agenda.
         */
        fun isLiveAt(nowMillis: Long): Boolean {
            if (triggerAt > nowMillis) return false
            return !fromStart || nowMillis - triggerAt <= ReminderDefaults.MISSED_GRACE_MILLIS
        }
    }
}
