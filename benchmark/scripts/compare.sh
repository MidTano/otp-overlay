#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#
# Compare two benchmark runs and print a diff report (Linux / macOS).
# See compare.ps1 for the Windows equivalent and benchmark/README.md
# for the rationale.
#
# Usage:
#   ./benchmark/scripts/compare.sh before.txt after.txt [threshold-pct]
#
# threshold-pct defaults to 10 (anything within +/-10% is reported
# as a wash). Exits 0 always — informational tool, not a gate.

set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "Usage: $0 <before.txt> <after.txt> [threshold-pct]" >&2
    exit 1
fi
before_file="$1"
after_file="$2"
threshold_pct="${3:-10}"

if [ ! -f "$before_file" ]; then echo "Not found: $before_file" >&2; exit 1; fi
if [ ! -f "$after_file" ]; then echo "Not found: $after_file" >&2; exit 1; fi

awk -v threshold="$threshold_pct" -v before_path="$before_file" -v after_path="$after_file" '
    function add(map, key, value) { map[key] = value }
    function read_run(path,    line, cls, test, median, key) {
        cls = ""; test = ""; median = -1
        while ((getline line < path) > 0) {
            if (line ~ /^INSTRUMENTATION_STATUS: class=/) {
                if (test != "" && median > 0) {
                    key = cls "::" test
                    if (path == before_path) before[key] = median
                    else after[key] = median
                }
                sub(/^INSTRUMENTATION_STATUS: class=/, "", line)
                cls = line; test = ""; median = -1
            } else if (line ~ /^INSTRUMENTATION_STATUS: test=/) {
                sub(/^INSTRUMENTATION_STATUS: test=/, "", line)
                test = line
            } else if (line ~ /^INSTRUMENTATION_STATUS: DEBUGGABLE_time_nanos_median=/) {
                sub(/^INSTRUMENTATION_STATUS: DEBUGGABLE_time_nanos_median=/, "", line)
                median = line + 0
            }
        }
        if (test != "" && median > 0) {
            key = cls "::" test
            if (path == before_path) before[key] = median
            else after[key] = median
        }
        close(path)
    }

    BEGIN {
        read_run(before_path)
        read_run(after_path)

        printf "\nComparing benchmark medians (threshold +/-%s%%)\n", threshold
        printf "  before: %s\n  after:  %s\n\n", before_path, after_path

        # Union of keys.
        for (k in before) keys[k] = 1
        for (k in after) keys[k] = 1

        improved = 0; regressed = 0; unchanged = 0; only_before = 0; only_after = 0

        # Sort keys.
        n = 0
        for (k in keys) sorted[n++] = k
        for (i = 1; i < n; i++) {
            for (j = 0; j < n - i; j++) {
                if (sorted[j] > sorted[j+1]) {
                    tmp = sorted[j]; sorted[j] = sorted[j+1]; sorted[j+1] = tmp
                }
            }
        }

        for (i = 0; i < n; i++) {
            k = sorted[i]
            short_name = k; sub(/^com\.midtano\.otp\.benchmark\./, "", short_name)
            if (!(k in before)) {
                only_after++
                printf "[NEW]   %-65s  before %12s  after %12d ns  delta %8s\n", short_name, "-", after[k], "new"
                continue
            }
            if (!(k in after)) {
                only_before++
                printf "[GONE]  %-65s  before %12d ns  after %12s  delta %8s\n", short_name, before[k], "-", "gone"
                continue
            }
            b = before[k]; a = after[k]
            delta_pct = ((a - b) / b) * 100
            abs_pct = (delta_pct < 0) ? -delta_pct : delta_pct
            if (abs_pct <= threshold) { tag = "[~]"; unchanged++ }
            else if (delta_pct < 0)   { tag = "[+]"; improved++ }
            else                       { tag = "[-]"; regressed++ }
            printf "%-7s %-65s  before %12d ns  after %12d ns  delta %+7.1f%%\n", tag, short_name, b, a, delta_pct
        }

        printf "\nSummary: %d improved, %d regressed, %d unchanged (within +/-%s%%), %d new, %d gone\n", \
               improved, regressed, unchanged, threshold, only_after, only_before
        printf "\nNotes:\n"
        printf "  - These are debuggable-build numbers (see benchmark/README.md). The\n"
        printf "    *delta* between before and after is what matters - both runs share\n"
        printf "    the same overhead so a 20%% improvement here is a 20%% improvement\n"
        printf "    in production too.\n"
        printf "  - Run on the same physical device, with the same charging state,\n"
        printf "    and with the screen on but idle. Different conditions invalidate\n"
        printf "    the comparison.\n"
    }
'
