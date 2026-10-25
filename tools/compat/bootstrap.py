"""Restore the public, hash-pinned compile dependencies for a clean CI checkout."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def restore(root=ROOT):
    lock = json.loads((root / "tools/compat/build-dependencies.json").read_text())
    generated = root / "porting/dependencies.lock.json"
    if generated.exists() and json.loads(generated.read_text()) != lock:
        raise ValueError("Existing porting/dependencies.lock.json differs; use a clean CI checkout")
    destination = root / "libs/pinned"
    destination.mkdir(parents=True, exist_ok=True)
    by_id = {row["mod_id"]: row for row in lock["artifacts"]}

    def obtain(row):
        path = destination / row["filename"]
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == row["sha256"]:
            return
        if "embedded" in row:
            parent = destination / by_id[row["embedded"]["parent"]]["filename"]
            with zipfile.ZipFile(parent) as archive:
                data = archive.read(row["embedded"]["path"])
        else:
            request = urllib.request.Request(row["url"], headers={"User-Agent": "NeoECO-compat/1"})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
        if hashlib.sha256(data).hexdigest() != row["sha256"]:
            raise ValueError(f"Build dependency hash mismatch: {row['mod_id']}")
        partial = path.with_suffix(".part")
        partial.write_bytes(data)
        partial.replace(path)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(obtain, [row for row in lock["artifacts"] if "embedded" not in row]))
    for row in lock["artifacts"]:
        if "embedded" in row:
            obtain(row)
    porting = root / "porting"
    porting.mkdir(exist_ok=True)
    generated.write_text(json.dumps(lock, indent=2) + "\n")
    # The private migration selection is not part of Git. A clean checkout uses the
    # complete checked-in test tree, without suppressing any production source.
    selection = porting / "upstream-sync-test-sources.txt"
    if not selection.exists():
        selection.write_text("\n".join(str(path.relative_to(root / "src/test/java"))
                            for path in sorted((root / "src/test/java").rglob("*.java"))) + "\n")
    print(f"Verified {len(lock['artifacts'])} compile dependencies")


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__).parse_args()
    restore()
