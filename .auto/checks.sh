#!/usr/bin/env bash
set -euo pipefail

# Keep the original test suite intact; no test filtering or ablation-specific exceptions.
./gradlew :app:test --no-daemon --quiet 2>&1 | tail -80
