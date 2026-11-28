#!/usr/bin/env bash
# Device check: runs the instrumented journey tests (app/src/androidTest, FakeLlm) on a USB-attached phone.
# Usage: scripts/device-check.sh   (set ANDROID_SERIAL when more than one device is attached)
# Saves build/reports/device/{instrument.txt,logcat.txt,screen.png}. Exits non-zero on any failure.
#
# Never uninstalls the app or clears its data: that deletes the model in its files/models/ dir. So it installs
# with `installDebug` (adb install -r keeps data) and runs `am instrument` itself instead of
# connectedDebugAndroidTest, which uninstalls the app after the run.
set -euo pipefail
PKG=dev.stoa.droid
RUNNER=$PKG.test/$PKG.FakeLlmRunner
MODELS=/sdcard/Android/data/$PKG/files/models
OUT=build/reports/device
cd "$(git rev-parse --show-toplevel)"

fail() {
  echo "device-check: $*" >&2
  exit 1
}

if [[ -z ${ANDROID_SERIAL:-} ]]; then
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  case $(grep -c . <<<"$devices" || true) in
    0) fail "no device attached. Plug in the phone with USB debugging on." ;;
    1) export ANDROID_SERIAL=$devices ;;
    *) fail "more than one device attached ($(echo $devices)); set ANDROID_SERIAL to pick one." ;;
  esac
fi
export ANDROID_SERIAL
[[ $(adb get-state 2>/dev/null) == device ]] || fail "$ANDROID_SERIAL is not ready (adb get-state). Unlock it and allow USB debugging."

wakefulness=$(adb shell dumpsys power | sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | head -1)
[[ $wakefulness == Awake ]] || fail "the screen of $ANDROID_SERIAL is off ($wakefulness). Wake and unlock it, then rerun."
adb shell dumpsys window | grep -q 'isKeyguardShowing=true' && fail "$ANDROID_SERIAL is locked. Unlock it, then rerun."

model_files() { adb shell "ls -l $MODELS 2>/dev/null" | tr -d '\r' | awk '/\.litertlm$/ { print $5, $NF }'; }
models_before=$(model_files)
check_models() {
  local now
  now=$(model_files)
  [[ $now == "$models_before" ]] || fail "MODEL FILES CHANGED during the $1 on $ANDROID_SERIAL!
  before: ${models_before:-none}
  after:  ${now:-none}
  Restore with: adb push <model>.litertlm $MODELS/"
}
[[ -n $models_before ]] && echo "model on phone: $models_before" || echo "no model on phone (the tests do not need one)"

rm -rf "$OUT" && mkdir -p "$OUT"
./gradlew -q :app:installDebug :app:installDebugAndroidTest
check_models install

adb logcat -c
start=$SECONDS
set +e
adb shell am instrument -w "$RUNNER" | tr -d '\r' | tee "$OUT/instrument.txt"
set -e
seconds=$((SECONDS - start))
adb logcat -d >"$OUT/logcat.txt"
adb exec-out screencap -p >"$OUT/screen.png"
check_models "test run"

if ! grep -qE '^OK \([0-9]+ tests?\)' "$OUT/instrument.txt"; then
  if grep -q 'FATAL EXCEPTION' "$OUT/logcat.txt"; then
    echo "--- crash in logcat ($OUT/logcat.txt):" >&2
    grep -A 15 'FATAL EXCEPTION' "$OUT/logcat.txt" | head -30 >&2
  fi
  fail "FAILED in ${seconds}s. Evidence in $OUT/: instrument.txt, logcat.txt, screen.png"
fi
echo "device-check: passed on $ANDROID_SERIAL in ${seconds}s ($(grep -oE '^OK \([0-9]+ tests?\)' "$OUT/instrument.txt")). Evidence in $OUT/"
