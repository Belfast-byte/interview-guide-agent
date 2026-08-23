#!/usr/bin/env bash

set -Eeuo pipefail

readonly PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly JAVA_21_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

backend_pid=''
frontend_pid=''

stop_processes() {
  trap - EXIT INT TERM
  [[ -z "$backend_pid" ]] || kill -- "-$backend_pid" 2>/dev/null || true
  [[ -z "$frontend_pid" ]] || kill -- "-$frontend_pid" 2>/dev/null || true
  [[ -z "$backend_pid" ]] || wait "$backend_pid" 2>/dev/null || true
  [[ -z "$frontend_pid" ]] || wait "$frontend_pid" 2>/dev/null || true
}

if [[ ! -x "$JAVA_21_HOME/bin/java" ]]; then
  echo "未找到 WSL Java 21：$JAVA_21_HOME/bin/java" >&2
  exit 1
fi
if ! command -v pnpm >/dev/null 2>&1; then
  echo "未找到 pnpm，请先安装项目要求的 pnpm。" >&2
  exit 1
fi

export JAVA_HOME="$JAVA_21_HOME"
export JDK_HOME="$JAVA_21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

trap stop_processes EXIT INT TERM

echo "后端：http://localhost:8080（Java $($JAVA_HOME/bin/java -version 2>&1 | head -n 1)）"
setsid "$PROJECT_DIR/gradlew" :app:bootRun --no-daemon &
backend_pid=$!

echo "前端：http://localhost:5173"
setsid pnpm --dir "$PROJECT_DIR/frontend" run dev &
frontend_pid=$!

wait -n "$backend_pid" "$frontend_pid"
