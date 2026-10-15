---
name: shealth-version-bump
description: Single-loop workflow for adding a new Samsung Health version to morphe-patches-samsung — discover the latest stable, download, pre-flight fingerprint/heuristic verification, fix drift, build, device-test. Use when asked to support a new Health version, check patch compatibility with a new APK, or bump AppTargets.
---

# Samsung Health Version Bump — Single Loop

One loop from "Samsung shipped a new Health" to "patch bundle supports it".
The Morphe patch bundle itself (val names, dex-only design, default-on) never
changes shape — only `Fingerprints.kt`, stubber heuristics, and `Constants.kt`
targets move.

## The loop

```
discover → scripts/version-loop.sh <version> → interpret report →
(fix drift if FAIL) → add AppTarget → build → device-test → conventional commit
```

### 1. Discover the latest stable (agent/web step)

The **version-watch workflow** (`.github/workflows/version-watch.yml`) polls
APKPure daily and auto-opens an `app-update` issue when a release not in
`Constants.kt` appears — start from that issue if it exists. Manually:

- `apkeep -a com.sec.android.app.shealth -l -d apk-pure /tmp` lists every
  APKPure version (newest last). APKMirror/APKPure block curl for pages
  (Cloudflare); the `apkeep -l` list is the reliable machine path.
- The newest upload may be a **beta**. The bump targets the newest
  **stable** release — cross-check the APKMirror listing via web tools.
- Compare against `Constants.kt` targets and `.cache/apks/`.

### 2. Run the mechanical loop (single command)

```bash
scripts/version-loop.sh <version>   # e.g. 7.00.6.011
scripts/version-loop.sh <version> --diff 7.00.6.011   # + dex diff vs a cached baseline
```

Stages: resolve/download APK via `apkeep` (APKPure source, cached under
`.cache/apks/`) → optional dex diff vs a cached baseline (`--diff`, stage 1a:
target-set delta report at `.cache/apks/dex-diff-*.md` with REA follow-up
commands) → pre-flight (`scripts/preflight_fingerprints.py`) → JSON report
at `.cache/apks/preflight-<version>.json`.

**Drift policy:** when a fingerprint drifts on a new version, prefer
migrating that target to a content-scanned stubber over re-pinning the exact
signature. ReVanced Patcher v22 replaced its whole fingerprint system with a
composable matching API for exactly this reason — exact class/name/flag
signatures are the component that breaks on app updates; content anchors
(strings, shapes) survive obfuscation churn. Exact fingerprints stay
appropriate only for stable SDK methods (`KnoxAdapter` and friends).

Pre-flight verifies, **without running Morphe**:

| Check | Source of truth |
|-------|-----------------|
| All 21 fingerprints (class, name, return, params, access-flag subset) | `Fingerprints.kt` |
| Which fingerprints are optional (`*IfPresent` call sites) | `KnoxBypassPatch.kt` / `AccountBypassPatch.kt` |
| Knox popup launcher / root checks / ViewModel gates / `$this$isRooted` | `OobeKnoxStubber.kt` heuristics |
| Obfuscated `isSakSupported` classes (`c6r`, `dc`, `qsv`, …) | `SakSupportedStubber.kt` heuristic |
| `SamsungAccountDataSourceImpl.invokeSuspend` provider fetch | `AccountProviderStubber.kt` heuristic |
| Discover+ promotion display path (`SHEALTH#PromotionDiscoverCardViewModel` tag) | `PromotionPanelStubber.kt` heuristic |
| `com.osp.app.signin` dex string presence | `SigninPackageReplacer.kt` scope |

### 3. Interpret the report

Severity mirrors the Kotlin patches — hard fingerprints throw on miss, scan
stubbers no-op on zero matches — so the report has two classes:

**Hard (FAIL, must fix before build):**

| Signal | Meaning | Fix |
|--------|---------|-----|
| `MISSING_CLASS` / `MISSING_METHOD` / `SIG_MISMATCH` / `FLAGS_MISMATCH` on a **non-optional** fingerprint | Stable SDK method changed | Inspect the new signature (step 3a), update `Fingerprints.kt` |
| `account_provider_fetch` → `NO_TARGET` (hits=0) | The 7.x login-critical ContentResolver→AccountManager rewrite has no target; login would break on-device only | Adjust `AccountProviderStubber.kt` heuristic (step 3a) |

**Informational (expected across versions, no action unless suspicious):**

| Signal | Why it is expected |
|--------|--------------------|
| `SamsungSakCheckerC6rFingerprint` missing | 7.x ships an empty `c6r` shell; `stubObfuscatedSakCheckers()` covers it |
| `SamsungAccountUtilsGetSamsungAccountIdFingerprint` missing | 7.x removed it; `stubAccountProviderFetchCalls()` is the 7.x path |
| `SamsungAccountUtilsIsAccountProviderSupportedFingerprint` (no-arg) mismatched | 6.3x variant; 7.x uses the `Context` variant |
| OOBE scans (`oobe_knox_popup_launcher`, `home_app_close_root_check`, `knox_handler_viewmodel_*`, `rooted_file_check`) at 0 hits | OOBE restructured after 6.32; zero hits verified fine on 7.00.0.107 |
| `promotion_display_path` at 0 hits | Build ships no Discover+ ad package — `hideHomePromotionPanelPatch` no-ops by design |
| `obfuscated_sak_checker` class names differ between builds | Renamed per build (`qsv` → `gtv` between 7.00.0.107 and 7.00.6.011) — that is why the scan exists |

#### 3a. Inspecting drift (modern decompilation flow)

**Automated path — REA drift evidence (start here):**

```bash
scripts/version-loop.sh <version> --rea          # adds stage [2a] right after pre-flight
# or standalone (extra --class flags inventory arbitrary classes too):
scripts/rea_drift.py --apk .cache/apks/com.sec.android.app.shealth@<v>.apk \
  --report .cache/apks/preflight-<v>.json --class com.some.ObfuscatedClass
```

Writes `.cache/apks/rea-drift-<version>.md` with, per failed fingerprint: the
expected definition from `Fingerprints.kt`, the actual class inventory (methods
with exact dex descriptors), and decompiled Java of any same-name method — the
SIG_MISMATCH vs MISSING_METHOD distinction becomes visible in one file.

Requirements: REA CLI (`npm install --global rea-agents`, node ≥ 22.19) and
`gh`. `scripts/rea.sh` auto-downloads the jadx-headless-mcp 0.7.1 engine JAR
into `.cache/tools/` on first use; it also passes through any REA command:

```bash
scripts/rea.sh inspect-android-method <apk> com.example.Class methodName   # decompile one method
scripts/rea.sh inspect-android-class  <apk> com.example.Class              # member inventory
scripts/rea.sh list-adb-devices                                            # device tooling (below)
```

Each REA call reloads the APK (~15 s) — use `rea_drift.py` for batches instead
of looping manually.

Surgical path — androguard, already a dependency:

```bash
python3 - <<'EOF'
import logging; logging.disable(logging.CRITICAL)
from androguard.core.dex import DEX
import zipfile
zf = zipfile.ZipFile(".cache/apks/com.sec.android.app.shealth@<version>.apk")
d = DEX(zf.read("classes.dex"))          # try classes2.dex … classes9.dex
for c in d.get_classes():
    if "security/KnoxAdapter" in c.get_name():
        for m in c.get_methods():
            print(m.get_name(), m.get_descriptor(), hex(m.get_access_flags()))
EOF
```

Richer path — install once (`brew install jadx`), then plugins:

```bash
jadx plugins --install "github:zinja-coder:jadx-ai-mcp"   # MCP: search/xrefs/smali for agents
jadx plugins --install "github:hoo-dles:jadx-morphe"      # test Morphe fingerprints live in JADX
uv tool install git+https://github.com/zinja-coder/jadx-mcp-server   # companion MCP bridge (jadx_mcp_server)
```

Live agent analysis setup (jadx-ai-mcp): open the APK in `jadx-gui` (needs
≥4 GB heap for ~350 MB APKs: `jadx-gui --help` for memory flags), the plugin
listens on port 8650; run `jadx_mcp_server` (uv-installed) as the MCP server
in your client (stdio), pointing at the default `--jadx-host 127.0.0.1
--jadx-port 8650`. The agent then gets `get_smali_of_class`,
`xrefs_to_method`, `search_classes_by_keyword`, rename/comment tools, etc.
`jadx-morphe` evaluates candidate `Fingerprint(...)` definitions against the
loaded APK directly in the GUI/CLI before editing `Fingerprints.kt`.

### 3b. Desktop patch + verify (no device needed)

`scripts/version-loop.sh <version> --patch` adds stage 4 after pre-flight:

0. **Version gate:** the patcher no-ops on versions missing from `Constants.kt` AppTargets — the script aborts with instructions if the version isn't declared. For a new candidate: pre-flight PASS → add AppTarget → rebuild → rerun `--patch`.

1. Resolves a `.mpp` bundle: `patches/build/libs/patches-*.mpp` if built, else the latest GitHub release artifact. To build the *current* tree you need `read:packages` on the gh token (`gh auth refresh -h github.com -s read:packages`) for the Morphe Maven registry, then `./gradlew :patches:buildAndroid generatePatchesList`.
2. Patches the APK headless with morphe-desktop (jar auto-downloaded to `.cache/tools/`, Java 21 via `/opt/homebrew/opt/openjdk@21` if present): `java -jar morphe-desktop-*-all.jar patch -p bundle.mpp app.apk`.
3. `scripts/verify_patched.py` re-indexes the patched dex and asserts: every hard fingerprint method is now a stub body, the AccountManager redirect landed (6.x `getSamsungAccountId` or 7.x `fetchAccountId$2`), no const-string still references `com.osp.app.signin`, and the promotion display path is stubbed (or the package is absent). Report: `.cache/apks/verify-<version>.json`.

A stage-4 PASS means the dex-level patch is proven; the emulator smoke test
(workflow `smoke-test.yml`, manual dispatch) additionally proves the patched
APK launches without crashing — run it before a device flash when touching
stubber code. The device test (step 6) remains the only runtime-behavior gate.

### 4. Add the target

`patches/src/main/kotlin/app/bigyank/patches/shared/Constants.kt` — add
`AppTarget(version = "x.y.z")` at the top of the list with a short comment.

### 5. Build

```bash
export GITHUB_TOKEN="$(gh auth token)"   # read:packages
./gradlew :patches:buildAndroid generatePatchesList clean
```

### 6. Device test (final gate — AGENTS.md checklist)

Morphe Manager, both patches on, 1280 MB runtime → launch, Samsung Account
login, sync. Bad logcat signals: `SignatureInfoDbHelper`, `AccountManagerProvider`,
`blocked application`. No device verification → do not claim support in docs.

REA also wraps caller-supplied adb for the device session — package install
with `--replace` data preservation, bugreport collection, build/window
inspection:

```bash
scripts/rea.sh list-adb-devices
scripts/rea.sh install-adb-package <serial> patched.apk --replace
scripts/rea.sh collect-adb-bugreport <serial> --output-directory .cache/apks/
```

### 7. Commit

Conventional commit — `feat: add Samsung Health x.y.z patch support`. Semantic
release publishes on push to `main`. Never commit unless the user asks.

## Hard rules (from AGENTS.md — still binding)

1. Never re-add `resourcePatch` (OOM on ~300 MB APK on-device).
2. Account patch stays dex-only.
3. Do not rename patch vals; do not hardcode obfuscated class names.
4. Fingerprint helpers are `BytecodePatchContext` extensions inside
   `fingerprint.method.apply { }`.
