#!/usr/bin/env bash
set -euo pipefail

root="app/src/main/java/interview/guide/modules/interview/agent/adaptive"

# Fast syntax/build guard before reporting a candidate metric.
./gradlew :app:compileJava --no-daemon --quiet

adaptive_prod_loc=$(find "$root" -type f -name '*.java' -print0 \
  | xargs -0 awk 'NF { count++ } END { print count + 0 }')
adaptive_prod_files=$(find "$root" -type f -name '*.java' | wc -l | tr -d ' ')
adaptive_spring_components=$(rg -l '^@(Component|Service|Repository|RestController|Controller|Configuration)\b' "$root" -g '*.java' | wc -l | tr -d ' ')
adaptive_test_files=$(find app/src/test/java/interview/guide/modules/interview/agent/adaptive -type f -name '*.java' | wc -l | tr -d ' ')

printf 'METRIC adaptive_prod_loc=%s\n' "$adaptive_prod_loc"
printf 'METRIC adaptive_prod_files=%s\n' "$adaptive_prod_files"
printf 'METRIC adaptive_spring_components=%s\n' "$adaptive_spring_components"
printf 'METRIC adaptive_test_files=%s\n' "$adaptive_test_files"
