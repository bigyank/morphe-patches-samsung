#!/usr/bin/env python3
"""Pre-flight fingerprint + heuristic verification for a Samsung Health APK.

Single-loop stage 2: checks every fingerprint in Fingerprints.kt and every
content-scan heuristic used by the Morphe patches (OobeKnoxStubber,
SakSupportedStubber, AccountProviderStubber, SigninPackageReplacer) against
an unpatched APK, before anything runs on-device or in a patcher.

Severity mirrors the Kotlin patches: hard fingerprints (fingerprint.method
throws on miss) and the login-critical account_provider_fetch scan are hard
requirements; all other scans stub whatever they find and no-op on zero hits,
so zero is informational.

Exit code 0 = green for an unchanged bundle. Exit 1 = drift that requires
Fingerprints.kt / stubber changes.

Usage:
    python3 scripts/preflight_fingerprints.py --apk <path.apk> [--json-out <path>]
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

logging.disable(logging.CRITICAL)
try:  # androguard logs via loguru, not stdlib logging
    from loguru import logger as _loguru_logger

    _loguru_logger.disable("androguard")
except ImportError:
    pass

REPO_ROOT = Path(__file__).resolve().parent.parent
SHEALTH_DIR = REPO_ROOT / "patches/src/main/kotlin/app/bigyank/patches/shealth"
FINGERPRINTS_KT = SHEALTH_DIR / "Fingerprints.kt"
CONSTANTS_KT = REPO_ROOT / "patches/src/main/kotlin/app/bigyank/patches/shared/Constants.kt"

# dexlib2 AccessFlags bits — fingerprint flags must be a subset of the method's.
ACCESS_FLAG_BITS = {
    "PUBLIC": 0x1,
    "PRIVATE": 0x2,
    "STATIC": 0x8,
    "FINAL": 0x10,
    "SYNCHRONIZED": 0x20,
    "VOLATILE": 0x40,
    "BRIDGE": 0x40,
    "TRANSIENT": 0x80,
    "VARARGS": 0x80,
    "NATIVE": 0x100,
    "INTERFACE": 0x200,
    "ABSTRACT": 0x400,
    "STRICT": 0x800,
    "SYNTHETIC": 0x1000,
    "ANNOTATION": 0x2000,
    "ENUM": 0x4000,
    "CONSTRUCTOR": 0x10000,
    "DECLARED_SYNCHRONIZED": 0x20000,
}


@dataclass
class Fingerprint:
    name: str
    defining_class: str
    method: str
    access_flags: list[str]
    return_type: str
    parameters: list[str]
    optional: bool = False


@dataclass
class DexMethod:
    name: str
    descriptor: str
    access_flags: int
    method_ref: object = field(repr=False, default=None)


def parse_fingerprints() -> list[Fingerprint]:
    """Regex-parse Fingerprints.kt. The file has a strictly uniform layout;
    fail loudly if it drifts so this parser is never silently wrong."""
    src = FINGERPRINTS_KT.read_text()
    block_re = re.compile(
        r"object\s+(\w+Fingerprint)\s*:\s*Fingerprint\(\s*"
        r"definingClass\s*=\s*\"([^\"]+)\",\s*"
        r"name\s*=\s*\"(\w+)\",\s*"
        r"accessFlags\s*=\s*listOf\(([^)]*)\),\s*"
        r"returnType\s*=\s*\"([^\"]+)\",\s*"
        r"parameters\s*=\s*(?:listOf\(([^)]*)\)|emptyList\(\)),",
    )
    fingerprints = []
    for m in block_re.finditer(src):
        flags = re.findall(r"AccessFlags\.(\w+)", m.group(4))
        params = re.findall(r'"([^"]+)"', m.group(6) or "")
        fingerprints.append(
            Fingerprint(
                name=m.group(1),
                defining_class=m.group(2).replace("\\$", "$"),
                method=m.group(3),
                access_flags=flags,
                return_type=m.group(5).replace("\\$", "$"),
                parameters=[p.replace("\\$", "$") for p in params],
            )
        )
    if not fingerprints:
        sys.exit("FATAL: parsed 0 fingerprints from Fingerprints.kt — layout changed?")
    return fingerprints


def mark_optional(fingerprints: list[Fingerprint]) -> None:
    """Fingerprints invoked via *IfPresent helpers are soft requirements."""
    optional_re = re.compile(
        r"(?:stubReturnFalseIfPresent|replaceMethodBodyIfPresent|stubZeroReturnIfPresent)"
        r"\(\s*(\w+Fingerprint)",
    )
    optional_names = set()
    for kt in SHEALTH_DIR.glob("*.kt"):
        optional_names.update(optional_re.findall(kt.read_text()))
    for fp in fingerprints:
        if fp.name in optional_names:
            fp.optional = True


def supported_targets() -> list[str]:
    src = CONSTANTS_KT.read_text()
    return re.findall(r'AppTarget\(version\s*=\s*"([^"]+)"\)', src)


def parse_descriptor(descriptor: str) -> tuple[list[str], str]:
    descriptor = descriptor.replace(" ", "")  # androguard pretty-prints separators
    m = re.match(r"\((.*)\)(.+)", descriptor)
    if not m:
        return [], descriptor
    params, ret = m.group(1), m.group(2)
    out, i = [], 0
    while i < len(params):
        if params[i] == "L":
            j = params.index(";", i)
            out.append(params[i : j + 1])
            i = j + 1
        else:
            out.append(params[i])
            i += 1
    return out, ret


def load_dex_index(apk_path: Path) -> tuple[dict[str, list[DexMethod]], list[bytes]]:
    """Index every class/method across all dex files. Uses raw DEX parsing
    (no cross-reference Analysis) so a ~350 MB APK stays tractable."""
    from androguard.core.dex import DEX

    index: dict[str, list[DexMethod]] = defaultdict(list)
    dex_buffers: list[bytes] = []
    with zipfile.ZipFile(apk_path) as zf:
        dex_names = sorted(
            n for n in zf.namelist()
            if re.fullmatch(r"classes\d*\.dex", n)
        )
        for n in dex_names:
            buf = zf.read(n)
            dex_buffers.append(buf)
            dex = DEX(buf)
            for cls in dex.get_classes():
                for meth in cls.get_methods():
                    index[cls.get_name()].append(
                        DexMethod(
                            name=meth.get_name(),
                            descriptor=meth.get_descriptor(),
                            access_flags=meth.get_access_flags(),
                            method_ref=meth,
                        )
                    )
            print(f"  indexed {n}: {len(index)} classes so far", file=sys.stderr)
    return index, dex_buffers


def check_fingerprint(fp: Fingerprint, index: dict) -> dict:
    methods = index.get(fp.defining_class)
    if not methods:
        return {"status": "MISSING_CLASS"}
    bits = 0
    for f in fp.access_flags:
        bit = ACCESS_FLAG_BITS.get(f)
        if bit is None:
            return {"status": "UNKNOWN_FLAG", "flag": f}
        bits |= bit
    candidates = [m for m in methods if m.name == fp.method]
    if not candidates:
        actual = sorted({m.name for m in methods})
        return {"status": "MISSING_METHOD", "class_methods": actual[:12]}
    for m in candidates:
        params, ret = parse_descriptor(m.descriptor)
        if params != fp.parameters or ret != fp.return_type:
            continue
        if m.access_flags & bits != bits:
            missing = [
                name for name, bit in ACCESS_FLAG_BITS.items()
                if bits & bit and not m.access_flags & bit
            ]
            # Deduplicate shared-bit synonyms (VOLATILE/BRIDGE etc.)
            seen, dedup = set(), []
            for name in missing:
                if name not in seen:
                    dedup.append(name)
                seen.add(name)
            return {
                "status": "FLAGS_MISMATCH",
                "actual_hex": hex(m.access_flags),
                "missing_flags": dedup,
            }
        return {"status": "OK"}
    return {
        "status": "SIG_MISMATCH",
        "actual": [m.descriptor for m in candidates],
    }


# ── Content-scan heuristics (mirror the Kotlin stubbers) ─────────────────────

def ins_name(ins) -> str:
    return ins.get_name()


def ins_output(ins) -> str:
    return ins.get_output() or ""


def method_strings(meth) -> set[str]:
    out = set()
    for ins in meth.method_ref.get_instructions():
        if ins_name(ins) in ("const-string", "const-string/jumbo"):
            out.add(ins_output(ins))
    return out


def method_has_instruction(meth, *names: str) -> bool:
    for ins in meth.method_ref.get_instructions():
        if ins_name(ins) in names:
            return True
    return False


def method_references(meth, class_sub: str, method_name: str) -> bool:
    for ins in meth.method_ref.get_instructions():
        if not ins_name(ins).startswith("invoke-"):
            continue
        out = ins_output(ins)
        if class_sub in out and f"->{method_name}(" in out:
            return True
    return False


def method_const_class_references(meth, type_sub: str) -> bool:
    for ins in meth.method_ref.get_instructions():
        if ins_name(ins) == "const-class" and type_sub in ins_output(ins):
            return True
    return False


def has_six_x_account_redirect(index: dict) -> bool:
    """6.x builds fetch the account id via SamsungAccountUtils.getSamsungAccountId;
    7.x removed it in favor of the SamsungAccountDataSourceImpl provider path."""
    for m in index.get("Lcom/samsung/android/sdk/healthdata/privileged/samsungaccount/SamsungAccountUtils;", []):
        if m.name == "getSamsungAccountId" and m.descriptor == "(Landroid/content/Context;)Ljava/lang/String;":
            return True
    return False


def run_heuristics(index: dict, dex_buffers: list[bytes]) -> list[dict]:
    """Mirror the Kotlin stubbers' semantics: scan heuristics stub whatever they
    find and silently no-op on zero matches, so only the account-fetch scan is
    'required' — and only on 7.x-style builds (6.x has its own redirect path).
    Zero hits elsewhere is expected (OOBE restructure after 6.32; APKPure
    universal variants) and reported as informational, plus drift intel when
    related markers exist in a shape the heuristics no longer match."""
    results = []
    six_x_redirect = has_six_x_account_redirect(index)

    def hits(name, count, classes, required=False):
        if count > 0:
            status = "OK"
        elif required:
            status = "NO_TARGET"
        else:
            status = "INFO"  # scan-and-stub heuristic, zero matches tolerated
        results.append(
            {
                "heuristic": name,
                "required": required,
                "hits": count,
                "classes": classes[:10],
                "status": status,
            }
        )

    # OobeKnoxStubber.kt — isKnoxPopupLauncher
    found = []
    for cls, methods in index.items():
        for m in methods:
            if m.descriptor in ("(Landroid/app/Activity;)V", "(Landroid/app/Activity;Z)V") or (
                m.descriptor.endswith(")V") and "Landroid/app/Activity;" in m.descriptor
            ):
                if m.method_ref is None:
                    continue
                strings = method_strings(m)
                if not (
                    any(k in s for s in strings for k in ("OOBE_ERROR_KNOX", "DP_DISCONNECTED_EXCEPTION"))
                    and any("extra_error_code" in s for s in strings)
                ):
                    continue
                if method_const_class_references(m, "HomeAppCloseActivity"):
                    found.append(f"{cls}->{m.name}")
    hits("oobe_knox_popup_launcher", len(found), found)

    # OobeKnoxStubber.kt — isHomeAppCloseRootCheck
    found = [
        f"{cls}->{m.name}"
        for cls, methods in index.items() if "HomeAppCloseActivity" in cls
        for m in methods
        if m.descriptor == "()Z" and m.method_ref
        and method_references(m, "KnoxControl", "checkWarrantyBit")
    ]
    hits("home_app_close_root_check", len(found), found)

    # OobeKnoxStubber.kt — KnoxHandlerViewModel flag getter/setter
    getters, setters = [], []
    for cls, methods in index.items():
        if "KnoxHandlerViewModel" not in cls:
            continue
        for m in methods:
            if m.method_ref is None:
                continue
            if m.descriptor == "()Z" and not m.access_flags & 0x8 and method_has_instruction(m, "iget-boolean"):
                getters.append(f"{cls}->{m.name}")
            if m.descriptor == "(Z)V" and not m.access_flags & 0x8 and method_has_instruction(m, "iput-boolean"):
                setters.append(f"{cls}->{m.name}")
    hits("knox_handler_viewmodel_getter", len(getters), getters)
    hits("knox_handler_viewmodel_setter", len(setters), setters)

    # OobeKnoxStubber.kt — isRootedFileCheck
    found = []
    for cls, methods in index.items():
        for m in methods:
            if m.descriptor != "(Ljava/io/File;)Z" or not m.access_flags & 0x8 or m.method_ref is None:
                continue
            if any("$this$isRooted" in s for s in method_strings(m)):
                found.append(f"{cls}->{m.name}")
    hits("rooted_file_check", len(found), found)

    # SakSupportedStubber.kt — obfuscated isSakSupported
    found = [
        f"{cls}->{m.name}"
        for cls, methods in index.items()
        if "service/health/security/sak/SakChecker" not in cls
        for m in methods
        if m.name == "isSakSupported" and m.descriptor == "()Z"
        and not m.access_flags & 0x400 and m.method_ref
    ]
    hits("obfuscated_sak_checker", len(found), found)

    # AccountProviderStubber.kt — SamsungAccountDataSourceImpl.invokeSuspend
    found = []
    for cls, methods in index.items():
        if "SamsungAccountDataSourceImpl" not in cls:
            continue
        for m in methods:
            if m.name != "invokeSuspend" or m.descriptor != "(Ljava/lang/Object;)Ljava/lang/Object;" or m.method_ref is None:
                continue
            if any("getSamsungAccountId" in s for s in method_strings(m)) and method_references(m, "ContentResolver", "call"):
                found.append(f"{cls}->{m.name}")
    # Login-critical on 7.x: if this scan misses, the account ID fetch still
    # hits Samsung Account's provider and login breaks (only visible on device).
    # 6.x builds have their own getSamsungAccountId redirect — not required there.
    hits("account_provider_fetch", len(found), found, required=not six_x_redirect)

    # SigninPackageReplacer.kt — raw occurrence count of the account package
    total = sum(buf.count(b"com.osp.app.signin") for buf in dex_buffers)
    results.append(
        {
            "heuristic": "signin_package_strings",
            "required": False,
            "hits": total,
            "classes": [],
            "status": "OK" if total > 0 else "INFO",
        }
    )

    # Drift intel: markers that exist but no longer match the Kotlin heuristic
    # shapes. The Kotlin stubbers skip them silently — worth knowing because a
    # gate may now be unstubbed on-device.
    intel = []
    if not (results[0]["hits"] and results[1]["hits"]):
        callers = []
        for cls, methods in index.items():
            for m in methods:
                if m.method_ref and method_references(m, "KnoxControl", "checkWarrantyBit"):
                    callers.append(f"{cls}->{m.name}")
        if callers:
            intel.append({"note": "checkWarrantyBit callers outside matched heuristic shapes", "classes": callers[:10]})
    if intel:
        results.append({"heuristic": "drift_intel", "required": False, "hits": len(intel), "classes": [], "status": "INTEL", "intel": intel})
    return results


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True, type=Path)
    ap.add_argument("--json-out", type=Path, default=None)
    args = ap.parse_args()

    if not args.apk.is_file():
        sys.exit(f"FATAL: APK not found: {args.apk}")

    version = re.search(r"@([\d.]+)\.apk$", args.apk.name)
    version = version.group(1) if version else "unknown"

    fingerprints = parse_fingerprints()
    mark_optional(fingerprints)
    targets = supported_targets()

    print(f"Pre-flight: {args.apk.name} (version {version})")
    print(f"Supported targets in Constants.kt: {', '.join(targets)}")
    if version != "unknown" and version not in targets:
        print(f"NOTE: {version} is NOT yet in Constants.kt — expected for a version bump.")
    print("Indexing dex files (raw DEX parse, no xref analysis)…", file=sys.stderr)
    index, dex_buffers = load_dex_index(args.apk)
    print(f"Indexed {len(index)} classes, {sum(len(v) for v in index.values())} methods")

    hard_fail, soft_warn = [], []
    fp_results = []
    for fp in fingerprints:
        res = {"fingerprint": fp.name, "optional": fp.optional, **check_fingerprint(fp, index)}
        fp_results.append(res)
        status = res["status"]
        if status == "OK":
            print(f"  OK       {fp.name}")
        else:
            line = f"  {status:16} {fp.name}" + ("  (optional)" if fp.optional else "")
            print(line)
            if status != "OK":
                (soft_warn if fp.optional else hard_fail).append(fp.name)

    print("\nContent-scan heuristics:")
    heuristics = run_heuristics(index, dex_buffers)
    for h in heuristics:
        req = " (required)" if h["required"] else ""
        print(f"  {h['status']:10} {h['heuristic']:32} hits={h['hits']}{req}")
        for c in h["classes"][:5]:
            print(f"             · {c}")
        for item in h.get("intel", []):
            print(f"             ⚠ {item['note']}:")
            for c in item["classes"][:6]:
                print(f"               · {c}")
        if h["status"] == "NO_TARGET":
            hard_fail.append(h["heuristic"])
        elif h["status"] == "INFO":
            soft_warn.append(h["heuristic"])

    report = {
        "apk": str(args.apk),
        "version": version,
        "supported_targets": targets,
        "fingerprints": fp_results,
        "heuristics": heuristics,
        "hard_failures": hard_fail,
        "optional_warnings": soft_warn,
    }
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(report, indent=2))

    print()
    if hard_fail:
        print(f"RESULT: FAIL — {len(hard_fail)} hard failure(s): {', '.join(hard_fail)}")
        print("Fix Fingerprints.kt / stubber heuristics before building.")
        return 1
    warn_note = f"; warnings: {', '.join(soft_warn)}" if soft_warn else ""
    print(f"RESULT: PASS — all hard checks OK{warn_note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
