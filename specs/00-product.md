# 00 — Product (functional spec)

WatchCal is a **standalone Wear OS app** that replaces the stock calendar notification experience with a **snooze-until-done reminder loop**, while letting the system do all calendar syncing.

## The one behavior that matters

A calendar event reminder on the watch should behave like a personal nag, not a fire-and-forget toast:

- When an event starts, the watch notifies with two actions: **Snooze** and **Done**.
- **Snooze** silences it for a fixed interval (default **10 minutes**), then it comes back. Indefinitely.
- **Done** dismisses it permanently — meaning *"I started / finished the task"*.
- **Swiping the notification away is a snooze, not a dismiss.** The only escape is Done. This is the core ergonomic: you cannot accidentally lose a task.

## Reminder lifecycle

Each event *instance* (a single occurrence of a possibly-recurring event) carries one reminder state:

```text
UPCOMING ──(begin time reached)──► NOTIFIED ──(Done)──► DONE   (terminal)
   ▲                                  │
   └──────(Snooze / swipe-away: silent for interval)──┘
```

- State is **per instance**, keyed by `eventId:beginTime`. A recurring event's occurrences are independent: marking Monday's standup Done says nothing about Tuesday's.
- If the event is moved on the phone, the instance key changes — the reminder resets to UPCOMING at the new time, and the stale state is pruned. This is correct: a moved event is a new commitment.
- Reminders fire **at event start**. `TODO NGH:` canonical expectation is honoring each event's own reminder minutes (the calendar mirror exposes a Reminders table); current implementation uses begin-time only; impact: users relying on "10 min before" leads see later notifications; closes: read `WearableCalendarContract.Reminders` and use the earliest lead per instance.
- **All-day events never notify.** They appear in the agenda only.
- An un-acted notification does not re-buzz on background refreshes; a snoozed one that comes back does buzz again.

## Agenda

The app screen is a minimal agenda:

- Lists event instances for the next **48 hours**, soonest first.
- Each row shows title, start time, and reminder state (upcoming / snoozed-until / done).
- Tapping a row marks it **Done** (same semantics as the notification action). `TODO NGH:` canonical expectation is tap-to-toggle with undo; current implementation is one-way Done; impact: accidental taps require waiting for re-sync of nothing — the state is permanent for that instance; closes: add an undo affordance.

## Sync

The user never configures accounts in WatchCal. Wear OS already mirrors the phone calendar to the watch; WatchCal reads that mirror. Consequences the user observes:

- Events appear/update on the watch within the OS's own sync cadence (typically fast) plus WatchCal's refresh (hourly background, plus on app open and at every alarm).
- Only the OS mirror window is visible (~1 day back, ~1 week forward) — enough for a 48h agenda.
- Read-only: no creating or editing events from the watch.

## Non-goals (MVP)

Explicit exclusions — out-of-scope is first-class:

- No event creation/editing, no phone companion app.
- No per-event snooze intervals or custom leads (fixed 10 min snooze).
- No tiles or complications yet (planned next ergonomics, after the loop proves itself).
- No own network sync, no Google Calendar API, no OAuth.
- No month/week browsing — 48h agenda only.
