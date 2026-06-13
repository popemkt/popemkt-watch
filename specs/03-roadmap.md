# 03 — Roadmap (the path to the best reminder app for this watch)

Ordered, grounded in WatchCal's constraints: **no app audio output on the primary device** (vibration-first; `specs/learnings.md`), the **battery rule** (no polling, one alarm at a time), and **spec-as-source** (every item lands in 00/01 before code).

Status legend: ☐ todo · ◐ in progress · ☑ done.

## P0 — Make it feel instant

Performance is judged on **release builds only** — a `debuggable` debug build makes Compose do heavy per-frame work on the weak watch CPU and is not representative.

- ☑ Release variant is sideloadable for on-watch testing (debug-key-signed, R8, baseline profile slot). `assembleRelease` → installable APK.
- ◐ **Baseline Profile** — a `com.android.test` producer module + `BaselineProfileGenerator` journey (startup + agenda scroll), packaged via the `androidx.baselineprofile` plugin + `profileinstaller`. Gives JIT-free startup and scroll. New entrypoint: `generateBaselineProfile` (requires a connected device or Wear emulator). This is the permanent jank fix; the release build is the interim one.
- ☐ Mark row view-models `@Immutable`; keep all grouping/formatting off the render path (done in `AgendaScreen.buildSections`).

## P1 — Nail the one behavior that matters

The reminder engine becomes airtight; removes the remaining heuristics.

- ☐ **Per-event lead times.** Close the existing `TODO NGH` in 00-product § Reminder lifecycle: read `WearableCalendarContract.Reminders`, fire at the earliest lead ("10 min before") instead of begin-time only.
- ☐ **Persist `notifiedAt` per instance.** Makes missed-detection exact (replaces the `MISSED_GRACE_MILLIS` heuristic) and lets the heads-up-only ring be bounded — closes the second `TODO NGH` in 01-architecture § Notifications.
- ☐ Resilience tests: Doze delivery, reboot re-arm, timezone change, locale change.

## P2 — Reach without opening the app (Wear-native surfaces)

- ☐ **Tile** — next reminder + Snooze/Done at a swipe (battery: no new wakeups; renders from persisted state).
- ☐ **Complication** — countdown to next event on the watch face.
- ☐ **Ongoing Activity** while a reminder is live.

## P3 — Interaction polish

- ☐ Rotary/bezel scroll + **haptics** on scroll detents and on Done/Snooze confirmation.
- ☐ Curved text, richer empty/permission states, screen transitions.

## P4 — Alerting robustness (given the dead speaker)

- ☐ Escalating, **off-body-aware** vibration patterns.
- ☐ Per-device **audio-capability detection** (reuse the `dumpsys`/playback probe) so devices that *can* ring do.
- ☐ (Stretch) phone-relay alert via Data Layer when the watch physically cannot ring — weigh hard against the battery rule and the standalone non-goal.

## P5 — Quality infra

- ☐ Compose UI tests + screenshot tests for the agenda/settings/alarm screens.
- ☐ Macrobenchmark startup/scroll regression gate.
- ☐ CI running `test detekt assembleRelease`.

---

`TODO NGH:` this roadmap is a living plan, not a contract — items graduate into 00/01 when picked up, and their decision records land there. Keep statuses current as work lands.
