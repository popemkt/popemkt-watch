# Changelog

## Unreleased

- Alarm-style full-screen alert: a due reminder lights the screen, takes it over (title + Snooze/Done), and rings + vibrates on the alarm stream until acted on; ring bounded at 60 s, then auto-snoozes. Any exit other than Done (swipe back, timeout) is a snooze. Heads-up fallback when the watch is in use.
- Configurable snooze interval: minutes + seconds steppers in a new in-app Settings screen (default 10 min, clamped 10 s – 60 min, stored in `ReminderSettingsStore` DataStore, read at snooze time).

- Bootstrap repo as spec-as-source monorepo (`apps/`, future `libs/`): `AGENTS.md` harness, `specs/` (functional + technical + code-unit cohesion contract + rules index), adapted minimally from the Draiver harness.
- WatchCal MVP (`apps/watchcal`): standalone Wear OS app reading the OS calendar mirror (`WearableCalendarContract`), snooze-until-done reminder loop (swipe-away = snooze, only Done dismisses), single re-armed exact alarm, hourly WorkManager safety net, Preferences DataStore state, Compose for Wear OS 48h agenda.
- Cohesion harness wired: Konsist L1 fences (test-enforced), detekt L2 sensors (warn-only, `config/detekt/detekt.yml`), L3 rubric in spec.
