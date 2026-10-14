#!/usr/bin/env python3
"""Generate drift evidence for failing pre-flight fingerprints using REA.

Reads a preflight-<version>.json report plus the APK it was produced from,
decompiles every fingerprint that did not match (SIG_MISMATCH / FLAGS_MISMATCH
/ MISSING_*), and writes a markdown report containing, per drifted fingerprint:

  1. the expected definition from Fingerprints.kt
  2. the actual class inventory (method + dex descriptor) via REA
  3. decompiled Java of the same-name method, when one exists

Output: .cache/apks/rea-drift-<version>.md — feed it to the drift-fix step
(skill: shealth-version-bump §3a) instead of hand-driving jadx.

Each REA call reloads the APK (~15 s), so runtime scales with drift count.

Usage:
  scripts/rea_drift.py --apk <apk> --report <preflight.json> [--class FQCN ...]
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from preflight_fingerprints import Fingerprint, parse_fingerprints  # noqa: E402

ENGINE_URL_REPO = "1013503897/jadx-headless-mcp"
ENGINE_VERSION = "0.7.1"


def resolve_engine() -> str:
    jar = os.environ.get(
        "REA_JADX_MCP_JAR",
        str(REPO_ROOT / ".cache/tools/jadx-headless-mcp-0.7.1-all.jar"),
    )
    if not Path(jar).is_file():
        sys.exit(
            f"FATAL: jadx-headless-mcp engine not found at {jar}\n"
            f"Run scripts/rea.sh once (auto-downloads v{ENGINE_VERSION}) or:\n"
            f"  gh release download v{ENGINE_VERSION} --repo {ENGINE_URL_REPO} "
            f"--pattern 'jadx-headless-mcp-{ENGINE_VERSION}-all.jar' --dir .cache/tools"
        )
    return jar


def rea_call(env: dict, *args: str) -> dict | None:
    """Run one rea command, returning parsed JSON or None on failure."""
    cmd = ["rea", *args, "--format", "json"]
    try:
        proc = subprocess.run(
            cmd, env=env, capture_output=True, text=True, timeout=300
        )
    except subprocess.TimeoutExpired:
        print(f"  ! rea timed out: {' '.join(args[:2])}", file=sys.stderr)
        return None
    out = proc.stdout.strip()
    if proc.returncode != 0 or not out:
        return None
    try:
        data = json.loads(out)
    except json.JSONDecodeError:
        return None
    if data.get("ok") is False:
        return None
    return data


def smali_to_java(cls: str) -> str:
    """REA wants dotted FQCNs; Fingerprints.kt stores smali descriptors."""
    if cls.startswith("L") and cls.endswith(";"):
        cls = cls[1:-1]
    return cls.replace("/", ".")


def class_inventory(data: dict) -> tuple[list[dict], list[dict]]:
    n = data.get("normalized_result", {})
    return n.get("methods", []) or [], n.get("fields", []) or []


def decompile_method(env: dict, apk: str, cls: str, method: str) -> str | None:
    data = rea_call(
        env, "inspect-android-method", apk, cls, method
    )
    if not data:
        return None
    return data.get("normalized_result", {}).get("source", {}).get("text")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--apk", required=True)
    ap.add_argument("--report", required=True, help="preflight-<version>.json")
    ap.add_argument(
        "--class",
        dest="extra_classes",
        action="append",
        default=[],
        help="also inventory this fully qualified class (repeatable)",
    )
    ap.add_argument("--out", help="output markdown path (default derived)")
    args = ap.parse_args()

    report = json.loads(Path(args.report).read_text())
    version = report.get("version") or Path(args.apk).stem
    failing = [f for f in report.get("fingerprints", []) if f.get("status") != "OK"]

    expected = {fp.name: fp for fp in parse_fingerprints()}
    targets: list[tuple[str, Fingerprint | None]] = [
        (f["fingerprint"], expected.get(f["fingerprint"])) for f in failing
    ]
    if args.extra_classes:
        for cls in args.extra_classes:
            targets.append((f"class:{cls}", None))
    if not targets:
        print("no failing fingerprints — nothing to inspect")
        return 0

    env = dict(os.environ)
    env["REA_JADX_MCP_JAR"] = resolve_engine()
    apk = str(Path(args.apk).resolve())
    out_path = Path(
        args.out
        or str(REPO_ROOT / ".cache/apks" / ("rea-drift-%s.md" % version))
    )

    sections: list[str] = [
        f"# REA drift evidence — {version}",
        "",
        f"Source APK: `{apk}`  ",
        f"Preflight report: `{args.report}`  ",
        f"Engine: jadx-headless-mcp {ENGINE_VERSION} via REA",
        "",
    ]
    errors = 0
    for name, fp in targets:
        print(f"· {name}")
        if fp is None:
            cls = smali_to_java(name.split("class:", 1)[1])
        else:
            cls = smali_to_java(fp.defining_class)
        data = rea_call(env, "inspect-android-class", apk, cls)
        sec = [f"## {name}", ""]
        if fp is not None:
            sec += [
                f"**Expected:** class `{fp.defining_class}`, method `{fp.method}`, "
                f"flags {fp.access_flags}, returns `{fp.return_type}`, "
                f"params {fp.parameters}",
                "",
            ]
        if data is None:
            errors += 1
            sec += [f"**Class not inspectable:** `{cls}` — REA returned no data "
                    "(absent from this build, or engine failure).", ""]
            sections += sec
            continue
        methods, fields = class_inventory(data)
        sec += [f"**Actual class inventory** — `{cls}`:", ""]
        if methods:
            sec += ["| method | dex descriptor |", "|---|---|"]
            for m in methods:
                sig = m.get("reported_signature", m.get("name", "?"))
                sec.append(f"| `{sig}` | `{m.get('dex_descriptor', '?')}` |")
        else:
            sec += ["_(no methods)_"]
        if fields:
            sec += ["", "Fields: " + ", ".join(
                f"`{f.get('name')}: {f.get('reported_type', '?')}`" for f in fields
            )]
        sec += [""]

        if fp is not None:
            names = {m.get("name") for m in methods}
            if fp.method in names:
                src = decompile_method(env, apk, cls, fp.method)
                if src:
                    sec += [f"**Decompiled `{fp.method}`:**", "", "```java",
                            src, "```", ""]
                else:
                    errors += 1
                    sec += [f"_decompilation of `{fp.method}` failed_", ""]
            else:
                sec += [f"_method `{fp.method}` absent from the class — see "
                        "inventory above for the replacement candidate._", ""]
        sections += sec

    out_path.write_text("\n".join(sections))
    print(f"report: {out_path} ({len(targets)} target(s), {errors} inspection error(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
