"""Import only the explicitly allowed structure-terminal files from the locked legacy commit."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "porting/structure-terminal-import.json"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    args = parser.parse_args()
    if MANIFEST.exists():
        raise SystemExit("Terminal import already recorded; refusing to overwrite subsequent adaptation.")
    lock = json.loads((ROOT / "porting/source-lock.json").read_text(encoding="utf-8"))["structure_terminal"]
    commit = lock["commit"]
    tracked = subprocess.check_output(["git", "-C", str(args.repository), "ls-tree", "-r", "--name-only", commit], text=True).splitlines()
    selected = [name for name in tracked if name.startswith("src/") and
                ("StructureTerminal" in Path(name).stem or "structure_terminal" in Path(name).stem)]
    entries = []
    for name in selected:
        target = (ROOT / name).resolve()
        if not target.is_relative_to(ROOT):
            raise RuntimeError(name)
        if target.exists():
            raise RuntimeError(f"Terminal input overlaps main input: {name}")
        data = subprocess.check_output(["git", "-C", str(args.repository), "show", f"{commit}:{name}"])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        entries.append({"path": name, "sha256": hashlib.sha256(data).hexdigest()})
    MANIFEST.write_text(json.dumps({"commit": commit, "status": "terminal files imported; required contracts/UI registration pending adaptation", "files": entries}, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(entries)} terminal-specific files; no other legacy source copied.")


if __name__ == "__main__":
    main()
