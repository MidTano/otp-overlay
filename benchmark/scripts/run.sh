#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#
# Convenience runner for the microbenchmark suite (Linux / macOS).
# See run.ps1 for the Windows equivalent and benchmark/README.md
# for the rationale behind the workflow.
#
# Usage:
#   ./benchmark/scripts/run.sh before.txt
#   # ... apply changes ...
#   ./benchmark/scripts/run.sh after.txt
#   ./benchmark/scripts/compare.sh before.txt after.txt

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <output-file>" >&2
    exit 1
fi
output="$1"

cd "$(dirname "$0")/../.."

# 1. Device check.
if ! adb devices | grep -q "device$"; then
    echo "No Android device connected. Run 'adb devices' to verify." >&2
    exit 1
fi

# 2. Build APKs.
echo "[1/4] Building app + benchmark APKs..."
./gradlew :app:assembleDebug :benchmark:assembleDebugAndroidTest --console=plain >/dev/null

# 3. Install.
echo "[2/4] Installing APKs on device..."
app_apk=$(find app/build/outputs/apk/debug -name '*.apk' | head -n1)
test_apk=$(find benchmark/build/outputs/apk/androidTest/debug -name '*.apk' | head -n1)
adb install -r -t "$app_apk" >/dev/null
adb install -r -t "$test_apk" >/dev/null

# 4. Run.
echo "[3/4] Running benchmarks (3-5 min, do not touch the device)..."
suppress="UNLOCKED,EMULATOR,UNSUSTAINED-ACTIVITY-MISSING,UNSUSTAINED-CLOCKS,DEBUGGABLE,LOW-BATTERY,METHOD-TRACING-ENABLED,UNLOCKED-NO-ACTIVITY"

mkdir -p "$(dirname "$output")" 2>/dev/null || true

adb shell am instrument -w -r --no-window-animation \
    -e androidx.benchmark.suppressErrors "$suppress" \
    com.midtano.otp.benchmark.test/androidx.benchmark.junit4.AndroidBenchmarkRunner \
    | tee "$output" \
    | awk '
        /^INSTRUMENTATION_STATUS: test=/ { sub(/^.*test=/, "  - "); print }
        /^Time:/                          { print "\033[32m" $0 "\033[0m" }
        /^OK \(/                          { print "\033[32m" $0 "\033[0m" }
        /^FAILURES!!!/                    { print "\033[31m" $0 "\033[0m" }
    '

# 5. Restore animations.
echo "[4/4] Restoring system animation scales..."
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0

echo
echo "Output saved -> $output"
echo
echo "Next step: run another pass after your changes, then compare:"
echo "  ./benchmark/scripts/compare.sh before.txt $output"
