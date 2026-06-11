# 01 — Architecture (technical spec)

How the implementation realizes [`00-product.md`](./00-product.md). Kotlin, Compose for Wear OS, no DI framework (manual graph in `App`).

## Monorepo shape

Gradle multi-project build — one repo, many apps:

```text
apps/        deployable applications (each `com.android.application` module = one APK)
  watchcal/  the Wear OS calendar reminder app (`:apps:watchcal`)
libs/        (future) shared Kotlin/Android libraries, extracted per the cohesion rule's
             PROMOTE verdict — a complete unit earns a lib
gradle/libs.versions.toml   single version catalog shared by all modules
config/detekt/detekt.yml    single L2 sensor config shared by all modules
```

New apps are added by `include(":apps:<name>")` in `settings.gradle.kts`. Cross-app sharing goes through `libs/`, never app→app imports (the L1 fence idea at repo scale). `TODO NGH:` canonical expectation: shared Android config (compileSdk, JDK target, detekt wiring) lives in a convention plugin under `build-logic/` once a second module exists; current implementation: configured directly in the single app module; impact: copy-paste drift when app #2 appears; closes: extract `build-logic` convention plugin with the second module.

## Package layout (= the L1 layer map)

```text
apps/watchcal/src/main/java/com/popemkt/watchcal/
  domain/      EventInstance, ReminderState, ReminderPlanner, ReminderDefaults   (leaf — pure Kotlin, no Android)
  calendar/    CalendarSource (interface) + WearCalendarSource (mirror reader)
  reminders/   ReminderStateStore, ReminderCoordinator, AlarmScheduler,
               ReminderNotifier, receivers, SyncWorker
  ui/          MainActivity, AgendaScreen
  App.kt       composition root — builds the object graph, owns channel + periodic worker
```

Layer fences (enforced by Konsist, see [`02-code-unit-cohesion.md`](./02-code-unit-cohesion.md)): `domain` imports no other layer; `calendar` may import `domain`; `reminders` may import `domain` + `calendar`; `ui` may import all. `App.kt` (root) is the only place concrete implementations are wired together.

## Data flow — one pipeline, one entrypoint

Every wakeup source funnels into a single idempotent operation, `ReminderCoordinator.refresh()`:

```text
 AlarmReceiver ─┐
 BootReceiver ──┤
 SyncWorker ────┼──► ReminderCoordinator.refresh()
 app open ──────┤        1. read 48h window from CalendarSource
 Snooze/Done ───┘        2. prune stale reminder state (keys no longer in window)
                         3. ReminderPlanner.plan(instances, states, now)   ← pure function
                         4. notify all due instances
                         5. schedule ONE exact alarm at plan.nextWakeMillis
```

`ReminderPlanner` is a pure function `(instances, states, now) → Plan(due, nextWakeMillis)` — the entire reminder semantics of `00-product.md` lives there, tested without Android.

## Battery design (the wakeup budget)

| Source | Cadence | Why it exists |
|---|---|---|
| Exact alarm (`setExactAndAllowWhileIdle`) | only at the next trigger time | the actual reminder; one scheduled at a time, chain re-arms itself |
| WorkManager periodic | 1 hour | safety net for event changes/moves while idle |
| `BOOT_COMPLETED` receiver | once per boot | alarms do not survive reboot |
| App open | user-initiated | freshness for the agenda |

No foreground service, no ContentObserver service, no own network. The OS calendar mirror is the sync engine. `TODO NGH:` canonical expectation: a `ContentObserver` registered while the app process is alive would catch event edits faster than the hourly worker; current implementation relies on worker + alarm-time re-read; impact: an event moved within the hour may notify at the stale time once; closes: register an observer on the mirror URI in `App` process lifetime.

## Persistence

`ReminderStateStore` = Preferences DataStore, one entry per instance key:

```text
"st:<eventId>:<beginMillis>"  →  "done"  |  "snoozed:<untilMillis>"
```

Absence = UPCOMING. Pruning removes keys not present in the current 48h window (moved/deleted/past events) — the store stays O(window), never grows.

## Notifications

- One channel `reminders`, importance HIGH (vibrates on watch).
- Notification id = instance key hash; actions **Snooze** / **Done** are `PendingIntent`s into `ReminderActionReceiver`; `deleteIntent` (swipe-away) routes to **Snooze** — the spec's "swipe is a snooze".
- `setOnlyAlertOnce(true)`: background refreshes that re-post a still-due notification do not re-buzz; a snooze cancels the notification, so its return buzzes again. This implements the re-buzz rule in `00-product.md` mechanically.

## Permissions

| Permission | Why | Notes |
|---|---|---|
| `READ_CALENDAR` | read the OS calendar mirror | runtime-requested on first launch |
| `POST_NOTIFICATIONS` | API 33+ | runtime-requested on first launch |
| `USE_EXACT_ALARM` | API 33+: calendar apps qualify, no user grant | |
| `SCHEDULE_EXACT_ALARM` | API 30–32 fallback (`maxSdkVersion=32`) | scheduler falls back to inexact if revoked |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` | reboot re-arm; receiver work | |

## Toolchain

- Kotlin 2.0.x, AGP 8.7.x, Gradle 8.11.x, JDK 17 toolchain target.
- Compose for Wear OS (`androidx.wear.compose:compose-material`), `minSdk 30` (Wear OS 3), `targetSdk 34`.
- `androidx.wear:wear` for `WearableCalendarContract`.
- detekt (L2 sensors, warn-only — `config/detekt/detekt.yml`), Konsist in unit tests (L1 fences).
- Standalone wear app: `com.google.android.wearable.standalone = true`.

## Entrypoints

Gradle tasks are the only valid entrypoints:

| Task | Purpose |
|---|---|
| `./scripts/setup-toolchain.sh` | one-time per machine: provisions JDK 21, Gradle wrapper, Android SDK into `.tooling/` (gitignored) and writes `local.properties` |
| `./gradlew assembleDebug` | build APK |
| `./gradlew test` | unit tests (planner stories + Konsist fences) |
| `./gradlew detekt` | L2 smell report (never blocks) |
| `./gradlew installDebug` | deploy to connected watch/emulator |

Builds run with `JAVA_HOME=.tooling/jdk-21/Contents/Home` when the system JDK is incompatible with AGP.

## Decision records

- **Read the OS mirror; never sync ourselves.** Rejected: Google Calendar REST API on watch (OAuth on a watch, network radio cost, quota) and a phone companion with Data Layer (a second app to build and keep in sync). The mirror costs zero battery and zero auth; its ~1-week window is sufficient for a 48h agenda. Revisit only if a feature genuinely needs writes or far-future events.
- **One alarm, re-armed, instead of one alarm per event.** Rejected: scheduling N alarms per sync. The chain (`fire → refresh → schedule next`) keeps AlarmManager state trivially small, makes the wakeup budget auditable, and self-heals: every fire re-reads the world.
- **Instance key = `eventId:beginMillis`.** A moved event gets a fresh key (reminder resets — desired), recurring occurrences are independent (required by spec). Rejected: `Instances._ID` (opaque, can change across mirror resyncs).
- **Swipe-away routes to snooze.** The notification `deleteIntent` is the snooze intent. Rejected: treating swipe as dismiss (breaks the core "cannot accidentally lose a task" ergonomic) and `setOngoing` (user hostile, fights the system UI).
- **Manual object graph over Hilt.** One module, ~6 collaborators; a DI framework would be accidental complexity. The boundary radius is still honored: consumers depend on `CalendarSource` (interface), wiring happens only in `App`. Revisit when a second module appears.
- **Preferences DataStore over Room.** The state is a small flat map with O(window) size; a relational store buys nothing. Revisit if per-instance history or queries appear.
