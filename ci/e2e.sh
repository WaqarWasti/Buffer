#!/usr/bin/env bash
# Runs inside the emulator. Never exits early so screenshots are always collected.
PKG=app.buffer
TARGET=com.android.settings
OUT=ci-out
mkdir -p "$OUT"
RESULT="$OUT/result.txt"
: > "$RESULT"

pass() { echo "PASS  $1" | tee -a "$RESULT"; }
fail() { echo "FAIL  $1" | tee -a "$RESULT"; FAILED=1; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml 2>&1 | tee -a "$OUT/dump-log.txt"; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }
focus() { adb shell dumpsys window | grep -E "mCurrentFocus" | head -1; adb shell dumpsys activity activities | grep -E "topResumedActivity" | head -1; }
launch_target() { adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }

adb install -r app/build/outputs/apk/debug/app-debug.apk || { fail "install"; exit 0; }
adb shell am force-stop "$PKG"
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs'"
adb shell "run-as $PKG sh -c 'cat > shared_prefs/buffer.xml'" < ci/buffer_prefs.xml
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.service.AppWatcherService"
adb shell settings put secure accessibility_enabled 1
sleep 3

# 1. Dashboard renders
adb shell am start -W -n "$PKG/app.buffer.ui.dashboard.MainActivity" 2>&1 | tee "$OUT/am-start-dashboard.txt"
sleep 5
shot 1-dashboard; dump 1-dashboard
grep -q "Buffer" "$OUT/1-dashboard.xml" && pass "dashboard shows" || fail "dashboard shows"
grep -q "Settings" "$OUT/1-dashboard.xml" && pass "dashboard lists apps" || fail "dashboard lists apps (may be scrolled off)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# 2. Opening a blocked app shows the buffer
launch_target
sleep 1
shot 2-breath
sleep 1.5
shot 3-facts; dump 3-facts
sleep 3
shot 4-buttons; dump 4-buttons
focus | tee "$OUT/focus-buffer.txt"
grep -q "BufferActivity" "$OUT/focus-buffer.txt" && pass "buffer appears when blocked app opens" || fail "buffer appears when blocked app opens"
grep -q "want to open" "$OUT/4-buttons.xml" && pass "decline button visible after countdown" || fail "decline button visible after countdown"
grep -q "Hold to continue" "$OUT/4-buttons.xml" && pass "hold-to-continue visible" || fail "hold-to-continue visible"

# 3. Back = decline, lands on home
adb shell input keyevent KEYCODE_BACK
sleep 2
focus | tee "$OUT/focus-after-back.txt"
grep -q "BufferActivity" "$OUT/focus-after-back.txt" && fail "back leaves buffer" || pass "back leaves buffer"
grep -q "$TARGET" "$OUT/focus-after-back.txt" && fail "back does not open blocked app" || pass "back does not open blocked app"
shot 5-after-back

# 4. Hold to continue opens the app and the buffer does not come back
launch_target; sleep 6
dump 6-before-hold
if XY=$(python3 ci/find_text.py "$OUT/6-before-hold.xml" "Hold to continue"); then
  adb shell input swipe $XY $XY 2500
  sleep 3
  focus | tee "$OUT/focus-after-continue.txt"
  grep -q "$TARGET" "$OUT/focus-after-continue.txt" && pass "continue opens the app" || fail "continue opens the app"
  shot 7-after-continue
  # Navigate inside the app: buffer must not reappear.
  adb shell input tap 540 600; sleep 2
  focus | tee "$OUT/focus-after-nav.txt"
  grep -q "BufferActivity" "$OUT/focus-after-nav.txt" && fail "no re-trigger while using the app" || pass "no re-trigger while using the app"
  shot 8-after-nav
else
  fail "could not find hold-to-continue to press"
fi

# 5. Leaving and reopening shows the buffer again
adb shell input keyevent KEYCODE_HOME; sleep 2
launch_target; sleep 2
focus | tee "$OUT/focus-reopen.txt"
grep -q "BufferActivity" "$OUT/focus-reopen.txt" && pass "buffer shows again on reopen" || fail "buffer shows again on reopen"
shot 9-reopen

adb logcat -d -t 2000 > "$OUT/logcat.txt"
grep -A12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -40 > "$OUT/crashes.txt"
[ -s "$OUT/crashes.txt" ] && fail "app crashed (see crashes.txt, logcat.txt)" || pass "no crashes"
exit 0
