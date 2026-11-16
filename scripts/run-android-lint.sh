#!/usr/bin/env bash
set -euo pipefail

mkdir -p build-diagnostics
(
  sleep 180
  date -u > build-diagnostics/lint-processes.txt
  ps -eo pid,ppid,comm,%cpu,%mem,rss >> build-diagnostics/lint-processes.txt
  free -m >> build-diagnostics/lint-processes.txt
  for pid in $(jps -q); do
    timeout 15 jcmd "$pid" Thread.print -l > "build-diagnostics/jvm-$pid-threads.txt" 2>&1 || true
    timeout 15 jcmd "$pid" GC.heap_info > "build-diagnostics/jvm-$pid-heap-summary.txt" 2>&1 || true
  done
) &
diagnostic_pid=$!
trap 'kill "$diagnostic_pid" 2>/dev/null || true' EXIT

timeout --signal=TERM --kill-after=30s 12m ./gradlew :fermata:lintAutoRelease \
  --no-daemon --info --stacktrace --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g' \
  2>&1 | tee build-diagnostics/lint.log
