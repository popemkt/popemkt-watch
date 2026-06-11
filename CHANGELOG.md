# Changelog

## Unreleased

- Bootstrap repo as spec-as-source monorepo (`apps/`, future `libs/`): `AGENTS.md` harness, `specs/` (functional + technical + code-unit cohesion contract + rules index), adapted minimally from the Draiver harness.
- WatchCal MVP (`apps/watchcal`): standalone Wear OS app reading the OS calendar mirror (`WearableCalendarContract`), snooze-until-done reminder loop (swipe-away = snooze, only Done dismisses), single re-armed exact alarm, hourly WorkManager safety net, Preferences DataStore state, Compose for Wear OS 48h agenda.
- Cohesion harness wired: Konsist L1 fences (test-enforced), detekt L2 sensors (warn-only, `config/detekt/detekt.yml`), L3 rubric in spec.
