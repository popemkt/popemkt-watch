# 00 — Product (functional spec)

WatchCal is a **standalone Wear OS app** that replaces the stock calendar notification experience with a **snooze-until-done reminder loop**, while letting the system do all calendar syncing.

## The one behavior that matters

A calendar event reminder on the watch should behave like a personal nag, not a fire-and-forget toast:

- When an event starts, the watch notifies with two actions: **Snooze** and **Done**.
- **Snooze** silences it for the configured interval (default **10 minutes**), then it comes back. Indefinitely.
- **Done** dismisses it permanently — meaning *"I started / finished the task"*.
- **Swiping the notification away is a snooze, not a dismiss.** The only escape is Done. This is the core ergonomic: you cannot accidentally lose a task.

## Alarm-style alert

A due reminder takes over like an RTOS-watch alarm, not a passive notification:

- Alerts are **vibration-first**: a due reminder buzzes hard, always. The notification fires a multi-second salvo in every presentation; the full-screen takeover additionally **vibrates in a continuous loop while shown** — the RTOS-alarm feel — until acted on or the ring timeout auto-snoozes. Sound (where the device supports app audio at all — see `specs/learnings.md`) is a bonus layer, never the carrier.
- The bundled tone is a **gentle ~10-second chime** (a soft rising arpeggio struck as bells), not a piercing beep — attention comes from its *length*, not its harshness. It loops while the reminder is unacted; it is **easy on the ears by design**.
- When the trigger fires and the screen is off/locked, a **full-screen alert** additionally lights the screen and takes it over: event title, start time, and two big buttons — **Snooze** and **Done**.
- The full-screen takeover is **bounded**: after the ring timeout (default **60 seconds**) with no action, it **auto-snoozes** — same semantics as pressing Snooze. The nag loop guarantees it returns; an unattended watch never rings forever.
- Dismissing the full-screen alert any way other than **Done** (swipe back, ring timeout) is a **snooze**.
- If the user is actively using the watch (or ambient display keeps the screen technically on, e.g. charging) the system shows a heads-up notification instead of the takeover — but the ring loop still sounds, and the same Snooze/Done actions apply. `TODO NGH:` canonical expectation: the heads-up-only ring is also bounded at the ring timeout; current implementation: it rings until acted on (bounding requires persisting first-notified time per instance); impact: an ignored reminder on a watch in use rings indefinitely; closes: track `notifiedAt` in the state store and auto-snooze from the coordinator.
- On Android 14+ the OS requires a **user grant for full-screen alerts** (sideloaded apps default to denied). When the grant is missing, the app's permission flow surfaces an "Allow full-screen alerts" step that deep-links to the system settings page; until granted, due reminders degrade to plain high-priority notifications.

## Settings

- **Snooze interval** is configurable in-app as **minutes + seconds** (default 10 min 0 s, clamped to 10 s – 60 min). One global value; applies to the next snooze, not retroactively to already-snoozed reminders.
- **Test sound** button: a single tap plays the bundled alert tone once on the alarm stream — verifies speaker + volume without waiting for a real reminder. (The multi-path diagnostic cycler used while bisecting the Xiaomi Watch 5 audio dead-end is dev-only and out of the release surface; see `specs/learnings.md`. On a device with no app audio output the tone is silently a no-op, by design.)

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
- **Missed events never ring.** A start-trigger is "live" only within a short grace (default **10 minutes**) of the start. If the app first sees an instance whose start passed earlier than that — a fresh install, a late phone sync, a long doze — it is **missed**: shown in the agenda (so it can be ticked Done or snoozed-to-retest) but **never auto-notified**. This is what stops a cold start from firing every event that already happened today at once. (Only Upcoming start-triggers are subject to the grace; a snooze-return always rings when due, however late.)
- **All-day events never notify.** They appear in the agenda only.
- An un-acted notification does not re-buzz on background refreshes; a snoozed one that comes back does buzz again.

## Agenda

The app screen is a minimal agenda:

- Shows **the whole calendar mirror** the watch holds — roughly a day or two back through a week forward — soonest order, **past included**. Earlier-today and yesterday's events stay visible so a Done or missed item can still be seen and acted on. (The agenda's reach is independent of the narrower window the firing pipeline scans for the next wake.)
- The current time of day is always visible (the Wear curved clock at the top of the screen).
- Rows are **grouped under day headers** — `Yesterday`, `Today`, `Tomorrow`, then weekday names, then dates (`MMM d`) for anything further out.
- Each row carries a **state glyph**, the title, the start time, and a state line:
  - **Upcoming, still ahead** — neutral glyph (`○`); state line is the lead context (`tap: done`, or `all day` for all-day events).
  - **Missed** (start already passed, never rung) — `!` glyph; state line reads `missed → tap: done`.
  - **Snoozed** — `Zz` glyph; state line reads `snoozed til <time> → reset`.
  - **Done** — `✓` glyph, the row visibly **muted** (dimmed, title struck through) so completed items recede.
- Tapping a row **cycles the reminder state**: upcoming/notified → **Done** (same semantics as the notification action) → **snoozed for one interval** (the undo — re-enters the nag loop and comes back) → **upcoming** (state cleared; fires at event start again if still ahead, otherwise it is simply missed). Done is therefore recoverable from the agenda; an accidental tap costs taps, never the task. The snoozed step doubles as a deliberate "ring me in N" test affordance for any event.
- The state line **names the next tap's outcome** (`tap: done`, `done → snooze`, `snoozed → reset`) so the one gesture is never a mystery.
- When the mirror is empty, the screen says so plainly (`Nothing in the mirror`) rather than showing a blank list.
- **Settings** is reached from a `⚙` gear chip pinned at the **top** of the agenda (directly under the clock) — reachable the instant the app opens, no scrolling. All-day events never carry a reminder and are shown for context only.

## Tile (watch widget)

A swipe away from the watch face, WatchCal offers a **tile** — a glanceable widget for the calendar without opening the app:

- Shows **one event at a time**: its day label (`Today`/`Tomorrow`/weekday/date), title, start time (or `all day`), and a position counter (`2 / 5`). Done/snoozed events carry the same state glyph (`✓`/`Zz`) the agenda uses.
- The tile is **forward-looking**: it cycles the events from now onward (anything not yet ended), soonest first. Past/missed items live in the app, not the glance.
- **Cycle with `‹` / `›`** buttons — step to the previous / next event in place. The tile **remembers the cursor** between glances, so you return to where you left off; it clamps back into range when the calendar shifts under it.
- **Tapping the event card cycles its reminder state** — the same one gesture as an agenda row: upcoming/missed → **Done** → **snoozed for one interval** → **upcoming**. The card names the next tap's outcome (`tap: done`, `done → snooze`, `snoozed → reset`) so the gesture is never a mystery, exactly as in the agenda. A done card is muted with a `✓`; a snoozed one shows `Zz`.
- An **`Open`** chip launches the full app (at the agenda) for the longer view and history.
- When nothing is ahead, the tile says `Nothing ahead`. Before calendar access is granted, it says so and the whole tile taps through to the app to grant.
- The tile **schedules nothing on its own and adds no periodic wakeup** — it renders from the same calendar mirror and persisted reminder state the app does. The only writes are **user-initiated**: a card tap routes through the same reminder action the agenda and the notification buttons use (which re-arms the single existing alarm — not a new wakeup source).

## Sync

The user never configures accounts in WatchCal. Wear OS already mirrors the phone calendar to the watch; WatchCal reads that mirror. Consequences the user observes:

- Events appear/update on the watch within the OS's own sync cadence (typically fast) plus WatchCal's refresh (hourly background, plus on app open and at every alarm).
- Only the OS mirror window is visible (~1 day back, ~1 week forward) — enough for a 48h agenda.
- Read-only: no creating or editing events from the watch.

## Non-goals (MVP)

Explicit exclusions — out-of-scope is first-class:

- No event creation/editing, no phone companion app.
- No per-event snooze intervals or custom leads (one global snooze interval, configurable in Settings).
- No tiles or complications yet (planned next ergonomics, after the loop proves itself).
- No own network sync, no Google Calendar API, no OAuth.
- No month/week browsing — 48h agenda only.
