#!/bin/sh
# Launch-liveness check for a patched Samsung Health APK on the CI emulator.
# Called as a single line by .github/workflows/smoke-test.yml — the
# android-emulator action runs multi-line scripts line-by-line as separate
# sh -c processes (no control flow, no variable state across lines), so all
# logic lives in this file instead.
#
# Env: PACKAGE  — android package name (com.sec.android.app.shealth)
# Exit 0 = process observed alive on 2+ polls and no crash signature;
#      1 = app never alive (emulator survived), or crash signature;
#      3 = emulator died before any live process was observed (inconclusive).
set -u

POLLS="${POLLS:-12}"
INTERVAL="${INTERVAL:-15}"
EVER_ALIVE=""

i=0
while [ "$i" -lt "$POLLS" ]; do
  i=$((i + 1))
  sleep "$INTERVAL"
  PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')"
  # capture evidence on every poll — a dying emulator must not lose the verdict
  adb logcat -d > smoke-logcat.txt 2>/dev/null || true
  adb exec-out screencap -p > smoke-screenshot.png 2>/dev/null || true
  if [ -n "$PID" ]; then
    EVER_ALIVE="$PID"
    echo "poll $i: pid=$PID"
    [ "$i" -ge 2 ] && break
  else
    echo "poll $i: no pid"
  fi
done

if [ -n "$EVER_ALIVE" ] && [ -f smoke-logcat.txt ]; then
  if grep -iE "VerifyError|FATAL EXCEPTION" smoke-logcat.txt; then
    echo "::error::crash signature in logcat (VerifyError/FATAL EXCEPTION)"
    exit 1
  fi
fi

if [ -n "$EVER_ALIVE" ]; then
  echo "SMOKE OK — process $EVER_ALIVE alive after launch"
  exit 0
fi

if adb get-state >/dev/null 2>&1; then
  echo "::error::patched app did not stay running — see smoke-logcat.txt artifact"
  exit 1
fi

echo "::error::emulator died before any live process was observed (inconclusive) — see artifacts if captured"
exit 3
