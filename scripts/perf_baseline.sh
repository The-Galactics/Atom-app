#!/usr/bin/env bash
# Capture frame + startup metrics for the Atom hero and Settings.
# Usage: scripts/perf_baseline.sh <package> [activity]
# Requires: a connected device/emulator (adb).
set -euo pipefail
PKG="${1:-com.atom.app}"
ACT="${2:-.MainActivity}"

echo "== Cold start (am start -W) =="
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT" | grep -E "TotalTime|WaitTime" || true

echo "== Resetting gfxinfo; exercise the hero (listening/thinking) now, then press Enter =="
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
read -r _

echo "== gfxinfo summary (jank %, frame buckets) =="
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky frames|95th|99th|Number Slow" || true

echo "Enable GPU overdraw debug in Developer Options and screenshot the hero for the overdraw figure."
