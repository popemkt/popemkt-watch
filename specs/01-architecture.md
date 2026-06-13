# 01 — Architecture (technical spec)

How the implementation realizes [`00-product.md`](./00-product.md). Kotlin, Compose for Wear OS, no DI framework (manual graph in `App`).

## Monorepo shape

Gradle multi-project build — one repo, many apps:

```text
apps/        deployable applications (each `com.android.application` module = one APK)
  watchcal/                 the Wear OS calendar reminder app (`:apps:watchcal`)
  watchcal-baselineprofile/ `com.android.test` baseline-profile producer for watchcal (P0 perf)
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
  ui/          MainActivity, AgendaScreen, SettingsScreen, AlarmActivity, AlarmVibrator
  App.kt       composition root — builds the object graph, owns channel + periodic worker
```

Layer fences (enforced by Konsist, see [`02-code-unit-cohesion.md`](./02-code-unit-cohesion.md)): `domain` imports no other layer; `calendar` may import `domain`; `reminders` may import `domain` + `calendar`; `ui` may import all. `App.kt` (root) is the only place concrete implementations are wired together.

## Data flow — one pipeline, one entrypoint

Every wakeup source funnels into a single idempotent operation, `ReminderCoordinator.refresh()`:

```text
 AlarmReceiver ─┐
 BootReceiver ──┤
 SyncWorker ────┼──► ReminderCoordinator.refresh()
 app open ──────┤        1. read scheduling window (now−6h … now+48h) from CalendarSource
 Snooze/Done ───┘        2. prune stale reminder state (keys no longer in window)
                         3. ReminderPlanner.plan(instances, states, now)   ← pure function
                         4. notify all due instances
                         5. schedule ONE exact alarm at plan.nextWakeMillis
```

`ReminderPlanner` is a pure function `(instances, states, now) → Plan(due, nextWakeMillis)` — the entire reminder semantics of `00-product.md` lives there, tested without Android. A start-trigger counts as **due** only while `now − beginMillis ≤ MISSED_GRACE_MILLIS` (10 min); older start-triggers are *missed* (never returned in `due`, and being `≤ now` they schedule no wake). A snooze-return has no grace — it fires whenever its `untilMillis` is reached. This is the design-level fix for the cold-start blast: an empty state store no longer turns every already-started event of the day into a notification.

Note the **two distinct windows**: the firing pipeline above scans `now−6h … now+SCHEDULING_FORWARD_MILLIS` (48h) just to decide what is due and when to wake; the **agenda UI** reads a far wider `now−AGENDA_LOOKBACK_MILLIS … now+AGENDA_FORWARD_MILLIS` (≈ the whole mirror, past included) purely for display. Display reach and firing reach are deliberately decoupled.

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

- One channel `reminders_v5`, importance HIGH (required for full-screen intent launch), **bundled gentle ~10 s chime** (`watchcal_alarm.wav`) set as the channel sound on the **`USAGE_NOTIFICATION`** stream, plus an aggressive multi-buzz vibration pattern (~5 s salvo; `FLAG_INSISTENT` does not loop vibration on the test device, so the pattern itself must carry the urgency). Superseded channels (`reminders`, `reminders_alarm`, `reminders_v3`, `reminders_v4`) are deleted on startup (channels are immutable — sound changes require a new channel id). `TODO NGH:` canonical expectation: the chime is audible on-device; current implementation: untested on the Xiaomi Watch 5, which has *no app audio output at all* (specs/learnings.md) — but the notification-usage path was the one branch never tried (the *alarm*-usage custom sound is what suppressed the whole alert), so this is the bisect's surviving half; impact: if the OEM still routes nothing, the alert stays vibration-only and the chime is a silent no-op (correct degradation); closes: confirm with `dumpsys notification` + a real fire whether the chime plays, and if not, accept vibration-only on this hardware.
- Every due notification carries `FLAG_INSISTENT`: the system loops the channel sound + vibration until the notification is cancelled (action taken / swipe-snoozed). This is what makes the ring independent of whether the full-screen takeover launches — ambient/AOD screens (charging) downgrade the FSI to heads-up, but the loop still sounds.
- Notification id = instance key hash; actions **Snooze** / **Done** are `PendingIntent`s into `ReminderActionReceiver`; `deleteIntent` (swipe-away) routes to **Snooze** — the spec's "swipe is a snooze".
- `setOnlyAlertOnce(true)`: background refreshes that re-post a still-due notification do not re-buzz; a snooze cancels the notification, so its return buzzes again. This implements the re-buzz rule in `00-product.md` mechanically.
- Category `CATEGORY_ALARM` + `setFullScreenIntent(...)` → `AlarmActivity`. Screen off/locked: the system launches the activity directly (lights screen via `setShowWhenLocked`/`setTurnScreenOn`). Screen in use: heads-up notification only — the spec's "no takeover mid-interaction" falls out of platform behavior.
- **Fence note:** `ReminderNotifier` (reminders layer) must not import `ui.AlarmActivity`. The full-screen `PendingIntent` is built by a factory lambda injected from `App` — the boundary stays interface-shaped, the root does the wiring.
- `AlarmActivity` owns the takeover's **continuous vibration loop** (`AlarmVibrator`: direct `Vibrator` with a repeating waveform — bypasses notification alert policy, which only plays the channel salvo once; `FLAG_INSISTENT` loops nothing on the test device). Started in `onStart`, stopped in `onStop`/on action. Activity lifecycle = the snooze guarantee: any exit other than Done (back/swipe dismiss, ring timeout via `RING_TIMEOUT_MILLIS`) snoozes the instance and cancels the notification.

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
- **Release builds are R8-minified and carry a baseline profile** (`androidx.baselineprofile` + `androidx.profileinstaller`). The `release` build type is signed with the **debug key for sideload testing only** — this is *not* a distribution key; a real key must replace it before any store release. Performance is always judged on a release build (a `debuggable` build makes Compose janky on the watch CPU; specs/03-roadmap.md § P0).
- **Baseline-profile producer module** `:apps:watchcal-baselineprofile` (`com.android.test`, `targetProjectPath = ":apps:watchcal"`) runs a startup + agenda-scroll journey to capture hot code. `useConnectedDevices = true` — generation needs a connected watch or Wear emulator.

## Entrypoints

Gradle tasks are the only valid entrypoints:

| Task | Purpose |
|---|---|
| `./scripts/setup-toolchain.sh` | one-time per machine: provisions JDK 21, Gradle wrapper, Android SDK into `.tooling/` (gitignored) and writes `local.properties` |
| `./gradlew assembleDebug` | build debug APK |
| `./gradlew assembleRelease` | build R8-minified, profiled, debug-key-signed APK (the perf-representative build) |
| `./gradlew test` | unit tests (planner stories + Konsist fences) |
| `./gradlew detekt` | L2 smell report (never blocks) |
| `./gradlew installDebug` | deploy to connected watch/emulator |
| `./gradlew :apps:watchcal:generateBaselineProfile` | run the journey on a connected device, package the baseline profile into the app |

Builds run with `JAVA_HOME=.tooling/jdk-21/Contents/Home` when the system JDK is incompatible with AGP.

## Decision records

- **Read the OS mirror; never sync ourselves.** Rejected: Google Calendar REST API on watch (OAuth on a watch, network radio cost, quota) and a phone companion with Data Layer (a second app to build and keep in sync). The mirror costs zero battery and zero auth; its ~1-week window is sufficient for a 48h agenda. Revisit only if a feature genuinely needs writes or far-future events.
- **One alarm, re-armed, instead of one alarm per event.** Rejected: scheduling N alarms per sync. The chain (`fire → refresh → schedule next`) keeps AlarmManager state trivially small, makes the wakeup budget auditable, and self-heals: every fire re-reads the world.
- **Instance key = `eventId:beginMillis`.** A moved event gets a fresh key (reminder resets — desired), recurring occurrences are independent (required by spec). Rejected: `Instances._ID` (opaque, can change across mirror resyncs).
- **Swipe-away routes to snooze.** The notification `deleteIntent` is the snooze intent. Rejected: treating swipe as dismiss (breaks the core "cannot accidentally lose a task" ergonomic) and `setOngoing` (user hostile, fights the system UI).
- **Manual object graph over Hilt.** One module, ~6 collaborators; a DI framework would be accidental complexity. The boundary radius is still honored: consumers depend on `CalendarSource` (interface), wiring happens only in `App`. Revisit when a second module appears.
- **Preferences DataStore over Room.** The state is a small flat map with O(window) size; a relational store buys nothing. Revisit if per-instance history or queries appear.
- **Insistent notification for the ring, full-screen intent for the takeover.** First implementation put the ring inside `AlarmActivity` (via FSI); on-device testing showed the system frequently downgrades FSI to heads-up (ambient/AOD counts as screen-on, e.g. while charging) — leaving the alert silent. The ring now lives on the notification (`FLAG_INSISTENT` + channel sound on the alarm stream), which sounds in every presentation; FSI remains for the screen takeover when allowed. Rejected: a foreground service ringer (battery rule; more moving parts) and forcing the takeover from the background (the OS forbids it — FSI *is* the sanctioned path).
- **Bundled gentle chime over device default ringtone, length over loudness.** The test device's default alarm tone is quiet and device tones vary unpredictably across watches (specs/learnings.md). WatchCal ships a generated tone (`res/raw/watchcal_alarm.wav`, produced deterministically by `scripts/generate-alarm-tone.py`, committed as an asset). The tone is a **gentle ~10 s rising-arpeggio bell chime** — pure additive sines, soft attack, long exponential decay, normalised below clipping — chosen after the original harsh dual-tone beep: attention rides the *duration* (it loops under `FLAG_INSISTENT` until acted), the timbre stays easy on the ears. It is the channel sound on the `USAGE_NOTIFICATION` stream (not `USAGE_ALARM`, which suppressed the whole alert on the Xiaomi Watch 5; specs/learnings.md). Rejected: forcing stream volume up (user hostile, fights system settings), `RingtoneManager` defaults (unpredictable loudness), and the harsh soft-clipped beep (the user asked for a longer, gentler alert).
- **Snooze interval read at snooze time, stored in DataStore.** The coordinator asks `ReminderSettingsStore` when a snooze happens; nothing caches the value. Rejected: pushing the interval into `ReminderPlanner` (the planner deals in absolute trigger times; intervals are an input to state transitions, not planning) and per-event intervals (spec non-goal).
- **Agenda is a Wear `Scaffold` (TimeText + Vignette) over a `ScalingLazyColumn`.** A `CompactChip` gear to Settings is pinned as the **first** item (directly under the clock — reachable without scrolling), followed by `ListHeader` day-group rows (`Yesterday`/`Today`/`Tomorrow`/weekday/`MMM d`, bucketed by local-midnight from `beginMillis`) and event chips; the full-screen-intent grant appears only as a single warning chip when the appop is missing. State is a leading text glyph (`○` ahead, `!` missed, `Zz` snoozed, `✓` done) plus muted chip colors + strikethrough for Done — no icon-font dependency (text glyphs match the existing `Zz`/`✓` buttons and honor the minimize-complexity rule). The secondary label names the next tap's outcome, keeping the one-gesture cycle self-documenting.
- **Agenda grouping + formatting is precomputed once per entries-change (`remember(entries)`), never on the render path.** `buildSections` buckets by day and pre-renders every per-row string (time, state line, glyph) into an immutable `DaySection`/`RowUi` model; the lazy block only emits prebuilt rows. This removes the `Calendar`/`DateFormat`/`groupBy` allocations that previously ran inside the list builder on every scroll frame — the fix for the observed scroll jank. The `AgendaEntry` domain model is unchanged; `RowUi` is a UI-local view model.
- **Sound-test is a single tap that plays the tone once.** `SettingsScreen` plays the bundled tone once on the alarm stream (the spec's "Test sound") — no cycling state, no on-screen ✓/✗ diagnostics. The five-variant playback cycler that bisected the Xiaomi audio dead-end was a dev affordance that had leaked into the release surface; it is removed (separation-of-concerns principle). The bisection recipe survives in `specs/learnings.md` and git history; restore a `BuildConfig.DEBUG`-gated probe if the next device needs audio-path validation.
