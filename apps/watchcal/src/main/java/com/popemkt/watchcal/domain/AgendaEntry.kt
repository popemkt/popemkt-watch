package com.popemkt.watchcal.domain

/**
 * A calendar instance paired with its reminder state — the view model both presentation
 * surfaces (`ui` agenda, `tile`) render from. Lives in `domain` so neither surface depends
 * on the other.
 *
 * @property state null = Upcoming (absence in the store).
 */
data class AgendaEntry(
    val instance: EventInstance,
    val state: ReminderState?,
)
