#!/usr/bin/env bash
# Device benchmark: TTFT, prefill/decode tokens/s and peak RAM of the real model on a USB-attached phone.
# Usage: scripts/bench.sh <model.litertlm> [gpu|cpu]   -> prints JSON lines, saves build/reports/bench.jsonl
set -euo pipefail
MODEL=${1:?usage: scripts/bench.sh <model.litertlm> [gpu|cpu]}
BACKEND=${2:-gpu}
PKG=dev.stoa.droid
DIR=/sdcard/Android/data/$PKG/files/models
NAME=$(basename "$MODEL")
cd "$(git rev-parse --show-toplevel)"

[[ $(adb get-state 2>/dev/null) == device ]] || { echo "No phone attached (adb get-state). Plug in the phone with USB debugging on." >&2; exit 1; }
[[ -f $MODEL ]] || { echo "Model file not found: $MODEL" >&2; exit 1; }

./gradlew -q :app:installDebug
adb shell mkdir -p "$DIR"
# Push once: the app's model dir survives reinstalls of the debug build.
if [[ $(adb shell "stat -c %s '$DIR/$NAME' 2>/dev/null" | tr -d '\r') != $(wc -c <"$MODEL" | tr -d ' ') ]]; then
  adb push "$MODEL" "$DIR/$NAME"
fi

adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.BenchActivity" --es model "$DIR/$NAME" --es backend "$BACKEND" >/dev/null
PID=$(adb shell pidof "$PKG" | tr -d '\r')
mkdir -p build/reports
for _ in $(seq 1 300); do
  if adb logcat -d -s STOA_BENCH:I STOA_BENCH:E -v raw | grep -q '^DONE$'; then break; fi
  # Native crashes (e.g. a bad model file) kill the process before it can log DONE; Android may restart the app
  # under a new pid, so watch the benchmark's own pid.
  if ! adb shell "[ -d /proc/$PID ]"; then
    echo "App died during the benchmark. First fatal log lines:" >&2
    adb logcat -d -v brief '*:F' | grep -vE 'native: #|runtime.cc|^\s+at |DEBUG' | head -5 >&2
    exit 1
  fi
  sleep 2
done
adb logcat -d -s STOA_BENCH:E -v raw | grep FAILED >&2 && exit 1
adb logcat -d -s STOA_BENCH:I -v raw | grep '^{' | tee build/reports/bench.jsonl
[[ -s build/reports/bench.jsonl ]] || { echo "No results within 10 minutes." >&2; exit 1; }
