#!/usr/bin/env python3
"""
Deterministic audit & metric verifier for JARVIS v1.0.1 open-source research & build outputs.
Outputs ONLY measured git commits, source counts, XML test totals, and APK artifacts.
"""
from __future__ import annotations

import hashlib
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOS_DIR = ROOT / "research" / "open-source" / "repos"
TEST_RESULTS_DIR = ROOT / "app" / "build" / "test-results" / "testDebugUnitTest"


def run_git(repo: Path, *args: str) -> str:
    if not (repo / ".git").exists():
        return "MISSING"
    res = subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        check=False,
    )
    return res.stdout.strip() if res.returncode == 0 else f"ERR:{res.stderr.strip()}"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    print("=== 1. GIT REPOSITORIES IN RESEARCH WORKSPACE ===")
    expected_repos = [
        "jarvis-upstream",
        "openphoneuse",
        "juyu-phone-agent",
        "openring",
        "zafiro",
        "androidharness",
        "openphone",
    ]
    for name in expected_repos:
        r = REPOS_DIR / name
        head = run_git(r, "rev-parse", "HEAD")
        branch = run_git(r, "rev-parse", "--abbrev-ref", "HEAD")
        date = run_git(r, "log", "-1", "--format=%cs")
        print(f" - {name:18s} branch={branch:10s} date={date:12s} commit={head}")

    print("\n=== 2. COMPLIANCE & EXCLUSION CHECKS (app/src/main) ===")
    forbidden_patterns = [
        re.compile(r"com\.secondly|openphone\.os|PolyForm", re.IGNORECASE),
        re.compile(r"quickjs|app\.cash\.quickjs", re.IGNORECASE),
    ]
    violations: list[str] = []
    for src in (ROOT / "app" / "src" / "main").rglob("*"):
        if src.is_file() and src.suffix in {".kt", ".java", ".xml", ".aidl"}:
            txt = src.read_text(encoding="utf-8", errors="ignore")
            for pat in forbidden_patterns:
                if pat.search(txt):
                    violations.append(f"{src.relative_to(ROOT)} matched {pat.pattern}")
    if violations:
        print("VIOLATIONS FOUND:")
        for v in violations:
            print("  !", v)
        return 1
    print(" - Clean: 0 OpenPhone/PolyForm or QuickJS references in app/src/main.")

    print("\n=== 3. CORE ARCHITECTURE CHECKS ===")
    agent_core = (ROOT / "app/src/main/java/com/example/core/agent/AgentCore.kt").read_text(encoding="utf-8")
    m = re.search(r"tools\s*=\s*listOf\((.*?)\)\s*\)", agent_core, re.DOTALL)
    tool_calls = re.findall(r"(\w+Tool)\(", m.group(1)) if m else []
    print(f" - ToolRegistryFactory registered tools: {len(tool_calls)}")

    db_file = (ROOT / "app/src/main/java/com/example/data/local/db/JarvisDatabase.kt").read_text(encoding="utf-8")
    ver_m = re.search(r"version\s*=\s*(\d+)", db_file)
    print(f" - Room Database version: {ver_m.group(1) if ver_m else 'UNKNOWN'} (has MIGRATION_7_8={'MIGRATION_7_8' in db_file})")

    gradle_file = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    vc_m = re.search(r"versionCode\s*=\s*(\d+)", gradle_file)
    vn_m = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_file)
    print(f" - App versionCode={vc_m.group(1) if vc_m else '?'}, versionName={vn_m.group(1) if vn_m else '?'}")

    print("\n=== 4. MEASURED JUNIT / ROBOLECTRIC XML RESULTS ===")
    if not TEST_RESULTS_DIR.exists():
        print(" - No XML test results directory found yet.")
    else:
        total_tests = 0
        total_failures = 0
        total_errors = 0
        total_skipped = 0
        for xml_path in sorted(TEST_RESULTS_DIR.glob("TEST-*.xml")):
            root_el = ET.parse(xml_path).getroot()
            t = int(root_el.attrib.get("tests", 0))
            f = int(root_el.attrib.get("failures", 0))
            e = int(root_el.attrib.get("errors", 0))
            s = int(root_el.attrib.get("skipped", 0))
            total_tests += t
            total_failures += f
            total_errors += e
            total_skipped += s
            suite = root_el.attrib.get("name", xml_path.name)
            print(f" - {suite}: tests={t}, failures={f}, errors={e}, skipped={s}")
        print(f" - TOTAL: tests={total_tests}, failures={total_failures}, errors={total_errors}, skipped={total_skipped}")
        if total_failures > 0 or total_errors > 0:
            return 2

    print("\n=== 5. APK OUTPUTS ===")
    apk_candidates = [
        ROOT / "JARVIS-v1.0.1.apk",
        ROOT / "app/build/outputs/apk/release/app-release.apk",
        ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk",
        ROOT / "app/build/outputs/apk/debug/app-debug.apk",
    ]
    for apk in apk_candidates:
        if apk.exists():
            size_mb = apk.stat().st_size / (1024 * 1024)
            print(f" - {apk.relative_to(ROOT)}: {apk.stat().st_size:,} bytes ({size_mb:.2f} MB) | SHA256={sha256_file(apk)}")
        else:
            print(f" - {apk.relative_to(ROOT)}: (not built)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
