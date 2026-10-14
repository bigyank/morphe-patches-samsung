#!/usr/bin/env bash
# REA (Reverse Engineer Anything) wrapper for this repo.
#
# Wires the caller-supplied jadx-headless-mcp engine REA requires, then execs rea.
# The engine JAR is not distributed by REA and is gitignored here; it is
# auto-downloaded from 1013503897/jadx-headless-mcp releases on first use.
#
# Usage:
#   scripts/rea.sh inspect-android-method <apk> <class> <method>   # decompile one method
#   scripts/rea.sh inspect-android-class  <apk> <class>            # one class's member inventory
#   scripts/rea.sh inspect-android-package <apk>                   # manifest + declaration overview
#   scripts/rea.sh decode-android-resources <apk>                  # apktool manifest/strings (res decode!)
#   scripts/rea.sh list-adb-devices                                # device-side tooling (logcat/bugreport/install)
#
# Any rea command/flag passes through (--format json|md|yaml, --filter-output, --llms …).
# Requires: node >= 22.19, npm i -g rea-agents, gh (for the engine download), java.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REPO_ROOT}/.cache/tools"
ENGINE_JAR="${TOOLS_DIR}/jadx-headless-mcp-0.7.1-all.jar"

if ! command -v rea >/dev/null 2>&1; then
  echo "FATAL: rea CLI not found. Install: npm install --global rea-agents (needs node >= 22.19)" >&2
  exit 1
fi

if [[ ! -f "$ENGINE_JAR" ]]; then
  echo "│ jadx-headless-mcp engine missing — downloading v0.7.1…"
  mkdir -p "$TOOLS_DIR"
  gh release download v0.7.1 --repo 1013503897/jadx-headless-mcp \
    --pattern 'jadx-headless-mcp-0.7.1-all.jar' --clobber --dir "$TOOLS_DIR"
fi
export REA_JADX_MCP_JAR="$ENGINE_JAR"

exec rea "$@"
