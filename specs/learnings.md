# Learnings — environment & device facts

Operational knowledge that is true of the *development environment and target hardware*, not of the codebase. Recorded so it survives sessions and agents; verify dates, hardware behavior can change with OS updates.

## Xiaomi Watch 5 (primary test device, Wear OS, Android 16 / SDK 36) — observed 2026-06-11

- **Wireless adb only** (charge cable has no data lines). Pair via `adb pair`, discover with `adb mdns services`; ports rotate per session.
- **adb drops seconds after screen-off** (Wi-Fi power save). Working pattern: background poll-loop `adb connect … && adb shell echo alive`, run the payload the moment it answers, wake the watch screen for pushes.
- **Full-screen intents are appop-gated on Android 14+.** The manifest permission `USE_FULL_SCREEN_INTENT` reports `granted=true` while the *appop* sits at `default` = silently rejected at notification-post time (`appops get com.popemkt.watchcal USE_FULL_SCREEN_INTENT` shows a `rejectTime`). Dev fix: `appops set … allow`. Product fix: the agenda's "Allow full-screen alerts" chip deep-links to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
- **Calendar mirror works under Mi Fitness pairing** but syncs lazily — minutes of lag between a phone-side edit and the mirror row. Verify with:
  `content query --uri content://com.google.android.wearable.provider.calendar/instances/when/<beginMs>/<endMs>`
- The watch's **default alarm ringtone is quiet**; WatchCal bundles its own tone (see 01-architecture decision record).
- Debug runtime permissions can be pushed: `pm grant com.popemkt.watchcal android.permission.READ_CALENDAR` (and `POST_NOTIFICATIONS`); `adb install -r` preserves grants.

## Build machine

- System JDK (GraalVM 25) too new for AGP — always `JAVA_HOME=.tooling/jdk-21/Contents/Home`; `./scripts/setup-toolchain.sh` provisions everything into gitignored `.tooling/`.
