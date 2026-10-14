#!/usr/bin/env bash
# Single-loop Samsung Health version bump: download → pre-flight → drift report → optional desktop patch + verify.
#
# Usage:
#   scripts/version-loop.sh <version>         # e.g. 7.00.6.011; download + pre-flight only
#   scripts/version-loop.sh <version> --patch # also build/patch/verify on desktop (stage 4)
#   scripts/version-loop.sh <version> --rea   # also decompile drifted fingerprints via REA
#   scripts/version-loop.sh latest            # apkeep unpinned (may grab a beta — prefer explicit)
#
# Version discovery (which build is latest stable) is an agent/web step — see
# .agents/skills/shealth-version-bump/SKILL.md. Everything mechanical runs here.
#
# Stages:
#   [1/3] resolve APK   — .cache/apks cache or apkeep (APKPure source)
#   [2/3] pre-flight    — fingerprint + heuristic verification (preflight_fingerprints.py)
#   [3/3] report        — exit 0 = green for Fingerprints.kt unchanged; exit 1 = drift
#   [2a]  --rea         — decompile every drifted fingerprint via REA + jadx-headless-mcp
#                         (scripts/rea_drift.py → .cache/apks/rea-drift-<version>.md)
#   [4/4] --patch       — morphe-desktop CLI patches the APK, verify_patched.py confirms stubs
#                         (requires .mpp bundle + read:packages token to build one — see below)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_DIR="${REPO_ROOT}/.cache/apks"
TOOLS_DIR="${REPO_ROOT}/.cache/tools"
VERSION=""
DO_PATCH=false
DO_REA=false
for arg in "$@"; do
  case "$arg" in
    --patch) DO_PATCH=true ;;
    --rea) DO_REA=true ;;
    *)
      if [[ -z "$VERSION" ]]; then VERSION="$arg"; else
        echo "usage: scripts/version-loop.sh <version|latest> [--patch] [--rea]" >&2; exit 2
      fi ;;
  esac
done

if [[ -z "$VERSION" ]]; then
  echo "usage: scripts/version-loop.sh <version|latest> [--patch] [--rea]"
  echo "  Discover the latest stable version first (APKMirror listing via web fetch)."
  exit 2
fi

echo "╭─ [1/3] resolve APK"
mkdir -p "$APK_DIR"
if [[ "$VERSION" == "latest" ]]; then
  echo "│ downloading latest (unpinned — verify it is not a beta)"
  apkeep -a com.sec.android.app.shealth -d apk-pure "$APK_DIR"
  APK="$(ls -t "$APK_DIR"/com.sec.android.app.shealth@*.apk | head -1)"
  VERSION="$(basename "$APK" | sed -E 's/.*@([0-9.]+)\.apk/\1/')"
  echo "│ resolved latest → ${VERSION}"
else
  APK="${APK_DIR}/com.sec.android.app.shealth@${VERSION}.apk"
  if [[ -f "$APK" ]]; then
    echo "│ cached: ${APK}"
  else
    echo "│ downloading ${VERSION} via apkeep (APKPure)…"
    apkeep -a "com.sec.android.app.shealth@${VERSION}" -d apk-pure "$APK_DIR"
  fi
fi
[[ -f "$APK" ]] || { echo "│ FATAL: APK missing after download: $APK"; exit 1; }

echo "├─ [2/3] pre-flight fingerprints + heuristics"
python3 "${REPO_ROOT}/scripts/preflight_fingerprints.py" \
  --apk "$APK" \
  --json-out "${APK_DIR}/preflight-${VERSION}.json"

if [[ "$DO_REA" == true ]]; then
  echo "├─ [2a] REA drift evidence (one REA call per target, ~15 s each)"
  python3 "${REPO_ROOT}/scripts/rea_drift.py" \
    --apk "$APK" \
    --report "${APK_DIR}/preflight-${VERSION}.json" \
    || echo "│ (REA drift evidence failed — continuing; run scripts/rea.sh once to install the engine)"
fi

echo "├─ [3/3] report"
echo "│ JSON: ${APK_DIR}/preflight-${VERSION}.json"
if [[ "$DO_PATCH" != true ]]; then
  echo "│ next: if PASS → add AppTarget(version = \"${VERSION}\") to Constants.kt, build, device-test"
  echo "│       if FAIL → fix Fingerprints.kt / stubber heuristics (see skill: shealth-version-bump)"
  echo "│       (run with --patch to also patch + verify on desktop before touching the device)"
  echo "╰─ done"
  exit 0
fi

echo "├─ [4/4] desktop patch + verify (experimental)"
# Morphe patches are version-gated: the patcher silently no-ops on versions
# missing from Constants.kt AppTargets. Fail fast with the actionable fix.
if ! grep -q "version = \"${VERSION}\"" "${REPO_ROOT}/patches/src/main/kotlin/app/bigyank/patches/shared/Constants.kt"; then
  echo "│ ${VERSION} is not in Constants.kt AppTargets — the patcher would skip every patch."
  echo "│ Add AppTarget(version = \"${VERSION}\"), rebuild the bundle, then rerun --patch:"
  echo "│   export GITHUB_ACTOR=bigyank GITHUB_TOKEN=\"\$(gh auth token)\"   # token needs read:packages"
  echo "│   ./gradlew :patches:buildAndroid generatePatchesList"
  exit 1
fi
JAR="$(ls "${TOOLS_DIR}"/morphe-desktop-*-all.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR" ]]; then
  echo "│ morphe-desktop jar missing — downloading latest release…"
  mkdir -p "$TOOLS_DIR"
  gh release download --repo MorpheApp/morphe-desktop --pattern 'morphe-desktop-*-all.jar' --clobber --dir "$TOOLS_DIR"
  JAR="$(ls "${TOOLS_DIR}"/morphe-desktop-*-all.jar | head -1)"
fi
MPP="$(ls "${REPO_ROOT}"/patches/build/libs/patches-*.mpp 2>/dev/null | grep -vE 'javadoc|sources' | head -1 || true)"
if [[ -z "$MPP" ]]; then
  echo "│ no local bundle — trying latest release artifact…"
  gh release download --repo bigyank/morphe-patches-samsung --pattern 'patches-*.mpp' --clobber --dir "$TOOLS_DIR" || true
  MPP="$(ls "${TOOLS_DIR}"/patches-*.mpp 2>/dev/null | grep -vE 'javadoc|sources' | head -1 || true)"
fi
if [[ -z "$MPP" ]]; then
  echo "│ FATAL: no .mpp bundle. Build one locally:"
  echo "│   export GITHUB_TOKEN=\"\$(gh auth token)\"   # token needs read:packages"
  echo "│   gh auth refresh -h github.com -s read:packages   # if missing"
  echo "│   ./gradlew :patches:buildAndroid generatePatchesList"
  exit 1
fi
echo "│ bundle: $(basename "$MPP")"
echo "│ patcher: $(basename "$JAR")"
JAVA_BIN="java"
[[ -x /opt/homebrew/opt/openjdk@21/bin/java ]] && JAVA_BIN="/opt/homebrew/opt/openjdk@21/bin/java"
MARKER="$(mktemp)"
( cd "$APK_DIR" && "$JAVA_BIN" -jar "$JAR" patch -p "$MPP" "$(basename "$APK")" ) | tail -5
# Output name varies by patcher version (<input>-<bundle>.apk or <input>-patched.apk)
PATCHED="$(find "$APK_DIR" -name '*.apk' ! -name "$(basename "$APK")" ! -name '*.mpp' -newer "$MARKER" -type f | head -1)"
rm -f "$MARKER"
[[ -n "$PATCHED" ]] || { echo "│ FATAL: patcher produced no output APK under $APK_DIR"; exit 1; }
echo "│ patched: $(basename "$PATCHED")"

echo "│ verifying stubs landed in patched dex…"
python3 "${REPO_ROOT}/scripts/verify_patched.py" \
  --original "$APK" \
  --patched "$PATCHED" \
  --json-out "${APK_DIR}/verify-${VERSION}.json"
echo "│ JSON: ${APK_DIR}/verify-${VERSION}.json"
echo "╰─ done"
