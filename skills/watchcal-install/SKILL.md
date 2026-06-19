---
name: watchcal-install
description: Build and install WatchCal release APKs onto the Xiaomi Watch 5 over wireless adb, including adb discovery, permission grants, and install verification.
---

# WatchCal Install

Use when asked to build/install WatchCal on the watch.

## Steps

1. Build release:

```sh
JAVA_HOME=.tooling/jdk-21/Contents/Home ./gradlew assembleRelease
```

2. Find the watch:

```sh
.tooling/android-sdk/platform-tools/adb devices -l
.tooling/android-sdk/platform-tools/adb mdns services
```

If no device appears, scan likely LAN hosts for the current wireless-debug port, then `adb connect` the live port:

```sh
seq 30000 50000 | xargs -P 256 -n 1 sh -c 'for ip in 192.168.2.19 192.168.2.18; do nc -G 1 -z "$ip" "$0" >/dev/null 2>&1 && echo "$ip:$0"; done'
.tooling/android-sdk/platform-tools/adb connect <ip:port>
```

3. Install:

```sh
.tooling/android-sdk/platform-tools/adb -s <serial-or-ip:port> install -r apps/watchcal/build/outputs/apk/release/watchcal-release.apk
```

4. Restore debug-time grants and launch:

```sh
.tooling/android-sdk/platform-tools/adb -s <serial> shell pm grant com.popemkt.watchcal android.permission.READ_CALENDAR
.tooling/android-sdk/platform-tools/adb -s <serial> shell pm grant com.popemkt.watchcal android.permission.POST_NOTIFICATIONS
.tooling/android-sdk/platform-tools/adb -s <serial> shell appops set com.popemkt.watchcal USE_FULL_SCREEN_INTENT allow
.tooling/android-sdk/platform-tools/adb -s <serial> shell am start -n com.popemkt.watchcal/.ui.MainActivity
```

5. Verify:

```sh
.tooling/android-sdk/platform-tools/adb -s <serial> shell dumpsys package com.popemkt.watchcal | rg 'versionCode|versionName|lastUpdateTime|READ_CALENDAR: granted|POST_NOTIFICATIONS: granted'
```

Notes: wireless adb ports rotate; stale candidates often show as `offline`. Disconnect stale entries before retrying.
