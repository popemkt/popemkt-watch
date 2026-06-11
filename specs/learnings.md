# Learnings — environment & device facts

Operational knowledge that is true of the *development environment and target hardware*, not of the codebase. Recorded so it survives sessions and agents; verify dates, hardware behavior can change with OS updates.

## Xiaomi Watch 5 (primary test device, Wear OS, Android 16 / SDK 36) — observed 2026-06-11

- **Wireless adb only** (charge cable has no data lines). Pair via `adb pair`, discover with `adb mdns services`; ports rotate per session.
- **adb drops seconds after screen-off** (Wi-Fi power save). Working pattern: background poll-loop `adb connect … && adb shell echo alive`, run the payload the moment it answers, wake the watch screen for pushes.
- **Full-screen intents are appop-gated on Android 14+.** The manifest permission `USE_FULL_SCREEN_INTENT` reports `granted=true` while the *appop* sits at `default` = silently rejected at notification-post time (`appops get com.popemkt.watchcal USE_FULL_SCREEN_INTENT` shows a `rejectTime`). Dev fix: `appops set … allow`. Product fix: the agenda's "Allow full-screen alerts" chip deep-links to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
- **Calendar mirror works under Mi Fitness pairing** but syncs lazily — minutes of lag between a phone-side edit and the mirror row. Verify with:
  `content query --uri content://com.google.android.wearable.provider.calendar/instances/when/<beginMs>/<endMs>`
- The watch's **default alarm ringtone is quiet**; WatchCal bundles its own tone (see 01-architecture decision record).
- **FSI downgrades to heads-up whenever the screen counts as "on"** — including ambient/AOD while charging. Logcat proof: zero `AlarmActivity` launches across several due-fires while docked. Any sound that lives only in the full-screen activity will silently never play; hence the insistent-notification ring design.
- **Alarm stream volume was 4/10 and is OEM-locked against shell writes** (`cmd media_session volume --stream 4 --set 10` is accepted but ignored). Only the watch Settings UI changes it.
- Sound-suppression checklist already ruled out (2026-06-12): DND/zen off, Android 15 notification cooldown off, channel verified on-device via `dumpsys notification` (sound URI + USAGE_ALARM + vibration pattern + FLAG_INSISTENT all present). If the insistent ring is still silent, next probe is the `buzzBeepBlink` attention decision in logcat during a screen-on fire — suspect Xiaomi sysui ("fusion center") interception.
- **Channel with custom `android.resource://` sound + `USAGE_ALARM` audio attributes = alert fully suppressed** (no sound AND no vibration) on this watch, while the plain default-sound channel buzzed fine. The OEM appears to special-case alarm-usage notification channels from third-party apps. Bisect remaining: custom tone on the notification stream vs default tone on the alarm stream, and whether `FLAG_INSISTENT` alone also suppresses.
- **The speaker works — but third-party audio routing is restricted** (2026-06-12): Mi Fitness "find watch" rings audibly, yet Google Assistant produces no speech, direct `MediaPlayer` playback with `USAGE_ALARM` (volume 1.0, WatchCal Test sound) is silent, the built-in timer app is broken, and shell volume writes (`cmd media_session volume --set`) are accepted-but-ignored for every stream (media pinned muted at 0). Hypothesis: the OEM only un-gates specific audio usages/streams for system components; bisect via WatchCal's usage-cycling Test sound button to find which `AudioAttributes.USAGE_*` actually reaches the speaker.
- `FLAG_INSISTENT` does **not** loop vibration on this watch — one pattern playback per notification post. Sustained attention must come from the pattern length itself + the snooze-return re-buzz cycle.
- Debug runtime permissions can be pushed: `pm grant com.popemkt.watchcal android.permission.READ_CALENDAR` (and `POST_NOTIFICATIONS`); `adb install -r` preserves grants.

## Build machine

- System JDK (GraalVM 25) too new for AGP — always `JAVA_HOME=.tooling/jdk-21/Contents/Home`; `./scripts/setup-toolchain.sh` provisions everything into gitignored `.tooling/`.
