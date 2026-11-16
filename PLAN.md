# 1sec-style app blocker: rebuild plan (Android, multi-app)

## What it does
When you open any app on your "monitored" list, a full-screen buffer appears first:
1. "It's time to take a deep breath..." (breathing animation)
2. Countdown, default 15s, configurable per app
3. Stats: attempts in last 24h, time saved, last use
4. Primary CTA "I don't want to open X" (goes Home). Secondary "Continue to X".

New vs. 1sec: a list of all installed apps, each with an on/off switch. Any number can be on at once, each with its own buffer length and settings.

## Stack
- Kotlin, Jetpack Compose (Material 3), minSdk 29, targetSdk current
- Room (monitored apps + attempt log), DataStore (global settings)
- Android `AccessibilityService` for detection, a plain `Activity` for the buffer screen
- No Hilt/DI needed at this size

## Architecture
```
app/
  data/       MonitoredApp, AttemptEvent, Room DAOs, StatsRepository
  service/    AppWatcherService (AccessibilityService)
  ui/buffer/  BufferActivity (countdown, stats, CTAs)
  ui/dashboard/ DashboardScreen (apps, buffer length, stats, setup health)
  ui/apps/    AppListScreen (search + toggles), AppDetailSheet (per-app seconds)
  ui/onboarding/ PermissionsScreen (guided setup + status checks)
```

### Data model
- `monitored_apps(package PK, enabled, bufferSeconds NULL = use global default, estMinutesPerOpen)`
- `attempts(id, package, timestamp, outcome: DECLINED | CONTINUED | ABANDONED)`
- Stats are queries, not stored: attempts in last 24h = `COUNT where ts > now-24h`; last use = `MAX(ts)`; time saved = `DECLINED count * estMinutesPerOpen` (or measured average session via UsageStats, optional).

### Detection (the core)
- `AppWatcherService` listens to `TYPE_WINDOW_STATE_CHANGED`, reads `event.packageName`.
- If package is in the enabled set, not the one-shot "just continued" flag, and not us/launcher/SystemUI:
  log attempt, launch `BufferActivity(package)`.
- Keep the enabled set in memory (Room Flow collected in the service). Toggling a switch takes effect instantly.
- Privacy hardening: update `serviceInfo.packageNames` at runtime to only the enabled apps, and set `canRetrieveWindowContent=false`. The service then never even sees events from other apps and never reads screen content.
- Backup: `SYSTEM_ALERT_WINDOW` so the buffer can be drawn over the app if Android blocks the background activity launch. Verify on your device which one is needed.

### Buffer flow
- "I don't want to..." -> log DECLINED, `performGlobalAction(GLOBAL_ACTION_HOME)`, finish.
- "Continue" -> log CONTINUED, launch the app's launcher intent, finish. There is NO pause/grace period: the next time you open the app from anywhere, the buffer shows again.
- Loop guard (not a feature, just correctness): "Continue" sets a one-shot flag so the app we just launched doesn't trigger its own buffer again. The flag is consumed by that first window event, or cleared as soon as you leave the app.
- Back/Home during countdown -> log ABANDONED, send Home.

### Multi-app UI
- Enumerate apps via `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` (declare in `<queries>`; no QUERY_ALL_PACKAGES needed).
- Searchable list, enabled apps pinned on top, one Switch per row.
- Tap a row for per-app buffer seconds and estimated minutes per open.
- Quick presets: "Social media" toggles Instagram, TikTok, X, Facebook, Reddit, YouTube, Snapchat if installed.

## Configuration dashboard (the app's main screen)
Opening the app itself (not the buffer) lands on a dashboard where all settings live, independent of the buffer screen:
- **Monitored apps:** searchable list of installed apps, a switch on each. Multiple on at once. Presets (e.g. Social media).
- **Buffer length:** a global default (slider/stepper, e.g. 1 to 60s, default 15s) plus an optional per-app override on each app's row.
- **Stats overview:** attempts today / 24h per app, times declined vs continued, estimated time saved, history list.
- **Setup health:** live status of Accessibility service, overlay permission, battery/autostart, with one-tap links to fix.
- **Other settings:** estimated minutes saved per declined open, breathing text on/off, change-protection delay (optional).

## Security and privacy ("use on my phone in a secure way")
- Do NOT declare `INTERNET`. The app physically cannot send data off the phone.
- `allowBackup=false`, `dataExtractionRules` excluding everything. No analytics, no crash SDKs, no ads.
- Accessibility service narrowed to the selected packages and no window content (above).
- Sideload only, signed with your own keystore. Don't publish to Play (its accessibility policy makes this hard); keep the repo private or open source so you can audit it.
- Only dependencies from Google/JetBrains (Compose, Room, DataStore); pin versions, enable Gradle dependency verification.
- Optional friction against your own impulses: delay or confirm before disabling the service or removing an app from the list.

## Device gotchas
- Accessibility service must be enabled manually in Settings once; Android shows a scary warning (expected; the app is yours).
- Your screenshots look like a Xiaomi/MIUI/HyperOS phone. Those aggressively kill background services. Plan for: Autostart on, Battery saver "No restrictions", lock the app in Recents, and "Display pop-up windows while running in background" allowed. The onboarding screen should link to each of these.
- Android 13+: sideloaded apps may need "Allow restricted settings" in App info before the accessibility toggle works.

## Milestones
1. Project scaffold, Room, theme (dark bg, lavender CTA like 1sec). Build APK in CI.
2. `AppWatcherService` + `BufferActivity` for a hard-coded single app. Prove detection and no loops on your phone.
3. Data layer and stats (24h attempts, last use, time saved).
4. Dashboard: app list with toggles, global + per-app buffer length, runtime `packageNames` narrowing.
5. Onboarding/permissions screen and MIUI instructions.
6. Polish: breathing animation, per-app settings, presets, history screen, release signing.

## Building it
No Android emulator is available in this cloud sandbox, so: Gradle build + unit tests here, GitHub Actions produces a signed APK artifact, you sideload and test on the phone. Detection behaviour (milestone 2) is the part that must be verified on a real device.

## Open decisions
- Buffer length: fixed 15s or per-app? (Plan: per-app, default 15s.)
- Time saved: flat estimate per declined open, or measured average session via Usage Access? (Plan: start flat, add measured later.)
