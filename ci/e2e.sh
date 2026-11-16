#!/usr/bin/env bash
# Runs inside the emulator. Never exits early so screenshots/logs are always collected.
# NOTE: no `uiautomator dump` during the flow. It registers a UiAutomation connection which
# suppresses real accessibility services (like Buffer's) and would invalidate the test.
PKG=app.buffer
TARGET=com.android.settings
OUT=ci-out
mkdir -p "$OUT"
RESULT="$OUT/result.txt"
: > "$RESULT"

pass() { echo "PASS  $1" | tee -a "$RESULT"; }
fail() { echo "FAIL  $1" | tee -a "$RESULT"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
top() { adb shell dumpsys activity activities | grep -E "topResumedActivity" | head -1 | tee "$OUT/top-$1.txt"; }
launch_target() { adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
bufferlog() { adb logcat -d -s BufferSvc:V; }
expect_top() { # name, regex, description
  top "$1" >/dev/null
  grep -Eq "$2" "$OUT/top-$1.txt" && pass "$3" || fail "$3"
}

adb install -r app/build/outputs/apk/debug/app-debug.apk || { fail "install"; exit 0; }
adb shell am force-stop "$PKG"
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs'"
adb shell "run-as $PKG sh -c 'cat > shared_prefs/buffer.xml'" < ci/buffer_prefs.xml
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.service.AppWatcherService"
adb shell settings put secure accessibility_enabled 1

# Wait for the accessibility service to actually connect.
for i in $(seq 1 30); do
  bufferlog | grep -q "service connected" && break
  sleep 1
done
bufferlog | grep -q "service connected" && pass "accessibility service connected" || fail "accessibility service connected"

# 1. Dashboard opens
adb shell am start -W -n "$PKG/app.buffer.ui.dashboard.MainActivity" >/dev/null
sleep 4
shot 1-dashboard
expect_top dashboard "app.buffer/.ui.dashboard.MainActivity" "dashboard opens"
adb shell input keyevent KEYCODE_HOME; sleep 2

# 2. Opening a blocked app shows the buffer
launch_target
sleep 1; shot 2-breath
sleep 1.5; shot 3-facts
sleep 3; shot 4-buttons
expect_top buffer "app.buffer/.ui.buffer.BufferActivity" "buffer appears when blocked app opens"

# 3. Back = decline, lands on home
adb shell input keyevent KEYCODE_BACK
sleep 2
shot 5-after-back
expect_top back "nexuslauncher" "back goes home, blocked app not opened"

# 4. Hold to continue opens the app, and using it does not bring the buffer back
launch_target; sleep 5
expect_top second "app.buffer/.ui.buffer.BufferActivity" "buffer shows on second open after declining"
adb shell input swipe 540 2150 540 2150 2500
sleep 3
shot 6-after-continue
expect_top continue "com.android.settings" "hold-to-continue opens the app"
adb shell input tap 540 700; sleep 2
shot 7-after-nav
expect_top nav "com.android.settings" "no buffer while using the app (still in settings)"

# 5. Leaving and reopening shows the buffer again
adb shell input keyevent KEYCODE_HOME; sleep 2
launch_target; sleep 2
shot 8-reopen
expect_top reopen "app.buffer/.ui.buffer.BufferActivity" "buffer shows again on reopen after continuing"

# 6. Final UI text check (one dump, at the very end, after the countdown)
sleep 4
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/final.xml" >/dev/null 2>&1
grep -q "want to open" "$OUT/final.xml" && pass "decline button text" || fail "decline button text"
grep -q "Hold to continue" "$OUT/final.xml" && pass "hold-to-continue text" || fail "hold-to-continue text"
grep -q "attempt" "$OUT/final.xml" && pass "attempt count text" || fail "attempt count text"

adb logcat -d > "$OUT/logcat.txt"
bufferlog > "$OUT/bufferlog.txt"
grep -A12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -40 > "$OUT/crashes.txt"
[ -s "$OUT/crashes.txt" ] && fail "app crashed (see crashes.txt)" || pass "no crashes"
exit 0
