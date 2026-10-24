"""Audit active Forge sources and an optional release JAR for NeoForge runtime residue."""
from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def json_residue(value, path, pointer=""):
    found = []
    if isinstance(value, dict):
        for key, part in value.items():
            location = f"{pointer}/{key}"
            if key == "neoforge_data" or key.startswith("neoforge:"):
                found.append({"path": path, "pointer": location, "value": key})
            found.extend(json_residue(part, path, location))
    elif isinstance(value, list):
        for index, part in enumerate(value):
            found.extend(json_residue(part, path, f"{pointer}/{index}"))
    elif isinstance(value, str) and value.startswith("neoforge:"):
        found.append({"path": path, "pointer": pointer, "value": value})
    return found


def audit(root, jar=None):
    findings = []
    json_files = 0
    java_files = 0
    for source in (root / "src/main/resources", root / "src/generated/resources"):
        for path in source.rglob("*.json"):
            json_files += 1
            findings.extend(json_residue(json.loads(path.read_text(encoding="utf-8")), path.relative_to(root).as_posix()))
    for path in (root / "src/main/java").rglob("*.java"):
        java_files += 1
        text = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)
        if re.search(r"net\.neoforged\.neoforge\b|\bNeoForge\b|neoforge:", text):
            findings.append({"path": path.relative_to(root).as_posix(), "value": "NeoForge Java runtime reference"})
    for path in (root / "src").rglob("neoforge.mods.toml"):
        findings.append({"path": path.relative_to(root).as_posix(), "value": "Obsolete NeoForge metadata"})
    packaged = None
    if jar:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            if "META-INF/mods.toml" not in names or "META-INF/neoforge.mods.toml" in names:
                findings.append({"path": str(jar), "value": "Incorrect loader metadata"})
            checked = 0
            for name in names:
                if name.endswith(".json"):
                    checked += 1
                    findings.extend(json_residue(json.loads(archive.read(name)), f"jar:{name}"))
                elif name.endswith(".class") and b"net/neoforged/neoforge/" in archive.read(name):
                    findings.append({"path": f"jar:{name}", "value": "NeoForge bytecode reference"})
            packaged = {"path": str(jar), "json_files": checked, "forge_metadata": "META-INF/mods.toml" in names}
    return {"java_files": java_files, "json_files": json_files, "findings": findings, "jar": packaged,
            "retained": [
                {"path": "build.gradle", "reason": "net.neoforged.moddev.legacyforge is the Forge 1.20.1 build plugin"},
                {"path": "settings.gradle", "reason": "NeoForged Maven hosts the LegacyForge build plugin"},
                {"path": "src/main/resources/META-INF/licenses/ae2-v19.2.18-NOTICE.txt", "reason": "Historical source attribution must retain its upstream tag"},
                {"path": "tools/porting, porting, docs", "reason": "Conversion inputs, test fixtures, approved baselines and historical evidence"}]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.root.resolve(), args.jar)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(bool(result["findings"]))
