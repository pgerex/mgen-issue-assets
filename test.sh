#!/usr/bin/env bash
set -uo pipefail

JAVA_CHOSEN=""
for candidate in /usr/lib/jvm/temurin-8 /usr/lib/jvm/temurin-11 /usr/lib/jvm/temurin-17; do
  if [ -x "$candidate/bin/java" ]; then JAVA_CHOSEN="$candidate"; break; fi
done
if [ -n "$JAVA_CHOSEN" ]; then
  export JAVA_HOME="$JAVA_CHOSEN"
fi

./gradlew --no-daemon --console=plain -q tapTest
