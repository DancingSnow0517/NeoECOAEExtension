"""Record existing validation evidence; this script never runs Gradle or marks runtime acceptance."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def read_log(path: Path) -> str:
    data = path.read_bytes()
    encoding = "utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    return data.decode(encoding, errors="replace")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record(log_path: Path) -> dict:
    log = read_log(log_path)
    # Gradle repeats javac diagnostics in its failure recap; count the actual compiler output once.
    compiler_log = log.partition("FAILURE: Build failed")[0]
    errors = re.findall(r"^(.+?\.java):(\d+): error: (.+)$", compiler_log, re.MULTILINE)
    declared = re.findall(r"^([\d,]+) errors?\s*$", compiler_log, re.MULTILINE)
    warnings = re.findall(r"^(\d+) warnings?\s*$", compiler_log, re.MULTILINE)
    files = Counter()
    messages = Counter()
    for file, _, message in errors:
        files[Path(file.strip()).relative_to(ROOT).as_posix()] += 1
        messages[message.rstrip()] += 1
    reports = {}
    for task in ("portingContractsTest", "portingPlatformTest", "portingRecipesTest"):
        xmls = sorted((ROOT / "build/test-results" / task).glob("TEST-*.xml"))
        if not xmls:
            continue
        totals = Counter(tests=0, failures=0, errors=0, skipped=0)
        for xml in xmls:
            suite = ET.parse(xml).getroot()
            for key in totals:
                totals[key] += int(suite.attrib.get(key, 0))
        reports[task] = {**totals, "reports": [
            {"path": p.relative_to(ROOT).as_posix(), "sha256": digest(p)} for p in xmls
        ], "scope": "isolated production classes; not full main or Forge runtime acceptance"}
    scope = json.loads((ROOT / "porting/scope-cleanup.json").read_text(encoding="utf-8"))
    unexpected = [entry["path"] for entry in scope["removed_files"] if (ROOT / entry["path"]).exists()]
    malformed = []
    json_count = 0
    for base in ("src/main/resources", "src/generated/resources", "porting"):
        for path in (ROOT / base).rglob("*.json"):
            json_count += 1
            try:
                json.loads(path.read_text(encoding="utf-8-sig"))
            except (ValueError, UnicodeError) as exc:
                malformed.append({"path": path.relative_to(ROOT).as_posix(), "error": str(exc)})
    return {
        "working_tree_parent": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "compile": {
            "log": log_path.relative_to(ROOT).as_posix(), "sha256": digest(log_path),
            "result": "failed" if errors else "inspect log",
            "reported_errors": int(declared[-1].replace(",", "")) if declared else None,
            "parsed_error_locations": len(errors),
            "reported_warnings": int(warnings[-1]) if warnings else None,
            "error_messages": dict(messages.most_common()),
            "files_by_error_count": dict(files.most_common()),
        },
        "isolated_tests": reports,
        "scope": {"removed_files": len(scope["removed_files"]), "unexpected_remaining_files": unexpected},
        "json_syntax": {"files": json_count, "invalid": malformed},
        "full_business_tests": "not run; main compilation incomplete",
        "current_business_runtime": "not run; main compilation incomplete",
        "minimal_environment_runtime": "historical checkpoint e24ea3b only; see environment-validation.json",
        "ordinary_forge_installation": "not verified",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", default="validation/compile-full-inventory.log")
    parser.add_argument("--output", default="porting/validation-state.json")
    args = parser.parse_args()
    result = record(ROOT / args.log)
    (ROOT / args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"compile": {k: v for k, v in result["compile"].items()
                                  if k not in ("files_by_error_count", "error_messages")},
                      "tests": {k: {x: y for x, y in v.items() if x != "reports"}
                                for k, v in result["isolated_tests"].items()},
                      "scope": result["scope"], "json_syntax": result["json_syntax"]}, ensure_ascii=False, indent=2))
