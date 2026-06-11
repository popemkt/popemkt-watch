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
                    ReminderState.Upcoming -> instance to instance.beginMillis
                    is ReminderState.Snoozed -> instance to state.untilMillis
                }
            }

        val due = pending.filter { (_, triggerAt) -> triggerAt <= nowMillis }
            .sortedBy { (_, triggerAt) -> triggerAt }
            .map { (instance, _) -> instance }
        val nextWakeMillis = pending
            .map { (_, triggerAt) -> triggerAt }
            .filter { it > nowMillis }
            .minOrNull()

        return Plan(due, nextWakeMillis)
    }
}
