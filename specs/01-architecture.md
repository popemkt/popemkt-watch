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
  reminders/   ReminderStateStore, ReminderSettingsStore, ReminderCoordinator,
               AlarmScheduler, ReminderNotifier, receivers, SyncWorker
  ui/          MainActivity, AgendaScreen, SettingsScreen, AlarmActivity
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

`ReminderSettingsStore` = a second Preferences DataStore (`reminder_settings`), one entry:

```text
"snooze_interval_millis"  →  Long   (absent = ReminderDefaults.SNOOZE_INTERVAL_MILLIS)
```

The setter clamps to [10 s, 60 min] (fail-fast: an out-of-range write is a bug upstream, the clamp keeps persisted state always valid). `ReminderCoordinator.snooze` reads it at snooze time — a changed interval applies to the next snooze, never retroactively.

## Notifications & the full-screen alarm

- One channel `reminders_alarm`, importance HIGH (required for full-screen intent launch), **channel sound = the bundled tone** (`android.resource://…/raw/watchcal_alarm`) with `USAGE_ALARM` audio attributes and a vibration pattern. The legacy soundless `reminders` channel is deleted on startup (channels are immutable — sound changes require a new channel id).
- Every due notification carries `FLAG_INSISTENT`: the system loops the channel sound + vibration until the notification is cancelled (action taken / swipe-snoozed). This is what makes the ring independent of whether the full-screen takeover launches — ambient/AOD screens (charging) downgrade the FSI to heads-up, but the loop still sounds.
- Notification id = instance key hash; actions **Snooze** / **Done** are `PendingIntent`s into `ReminderActionReceiver`; `deleteIntent` (swipe-away) routes to **Snooze** — the spec's "swipe is a snooze".
- `setOnlyAlertOnce(true)`: background refreshes that re-post a still-due notification do not re-buzz; a snooze cancels the notification, so its return buzzes again. This implements the re-buzz rule in `00-product.md` mechanically.
- Category `CATEGORY_ALARM` + `setFullScreenIntent(...)` → `AlarmActivity`. Screen off/locked: the system launches the activity directly (lights screen via `setShowWhenLocked`/`setTurnScreenOn`). Screen in use: heads-up notification only — the spec's "no takeover mid-interaction" falls out of platform behavior.
- **Fence note:** `ReminderNotifier` (reminders layer) must not import `ui.AlarmActivity`. The full-screen `PendingIntent` is built by a factory lambda injected from `App` — the boundary stays interface-shaped, the root does the wiring.
- `AlarmActivity` is presentation only — the sound/vibration loop is owned by the notification (`FLAG_INSISTENT`), so takeover and ring cannot drift apart. Activity lifecycle = the snooze guarantee: any exit other than Done (back/swipe dismiss, ring timeout via `RING_TIMEOUT_MILLIS`) snoozes the instance, which cancels the notification and therefore stops the ring.

Battery note (per the battery rule): the takeover holds the screen on (`FLAG_KEEP_SCREEN_ON`) for at most `RING_TIMEOUT_MILLIS` (60 s) per alert, then auto-snoozes. The insistent ring stops whenever the notification is cancelled. No wakeup source is added — everything rides the existing exact-alarm chain. (See the `TODO NGH:` in 00-product about bounding the heads-up-only ring.)

## Permissions

| Permission | Why | Notes |
|---|---|---|
| `READ_CALENDAR` | read the OS calendar mirror | runtime-requested on first launch |
| `POST_NOTIFICATIONS` | API 33+ | runtime-requested on first launch |
| `USE_EXACT_ALARM` | API 33+: calendar apps qualify, no user grant | |
| `SCHEDULE_EXACT_ALARM` | API 30–32 fallback (`maxSdkVersion=32`) | scheduler falls back to inexact if revoked |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` | reboot re-arm; receiver work | |
| `USE_FULL_SCREEN_INTENT` | launch `AlarmActivity` from a due notification | manifest permission granted at install, **but** API 34+ gates it behind an appop that defaults to deny for non-store apps. `MainActivity` checks `NotificationManager.canUseFullScreenIntent()` and offers a deep link to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`; denied = graceful degrade to heads-up |
| `VIBRATE` | alarm vibration loop | |

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
- **Insistent notification for the ring, full-screen intent for the takeover.** First implementation put the ring inside `AlarmActivity` (via FSI); on-device testing showed the system frequently downgrades FSI to heads-up (ambient/AOD counts as screen-on, e.g. while charging) — leaving the alert silent. The ring now lives on the notification (`FLAG_INSISTENT` + channel sound on the alarm stream), which sounds in every presentation; FSI remains for the screen takeover when allowed. Rejected: a foreground service ringer (battery rule; more moving parts) and forcing the takeover from the background (the OS forbids it — FSI *is* the sanctioned path).
- **Bundled alarm tone over device default ringtone.** The test device's default alarm tone is quiet and device tones vary unpredictably across watches (specs/learnings.md). WatchCal ships a generated dual-tone beep pattern (`res/raw/watchcal_alarm.wav`, produced by `scripts/generate-alarm-tone.py`, committed as an asset) and plays it at player volume 1.0 on the alarm stream. Rejected: forcing alarm *stream* volume up (user hostile, fights system settings) and `RingtoneManager` defaults (the original implementation — unpredictable loudness).
- **Snooze interval read at snooze time, stored in DataStore.** The coordinator asks `ReminderSettingsStore` when a snooze happens; nothing caches the value. Rejected: pushing the interval into `ReminderPlanner` (the planner deals in absolute trigger times; intervals are an input to state transitions, not planning) and per-event intervals (spec non-goal).
