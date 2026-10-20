"""Import the approved main archive without replacing target build files or documentation.

The initial import intentionally precedes API adaptation. It is not a compilable port.
The manifest records every imported byte before scope cleanup and subsequent edits.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "porting/main-import.json"
LOCK = ROOT / "porting/source-lock.json"
PREFIXES = ("src/", "guidebook/")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    args = parser.parse_args()
    if MANIFEST.exists():
        raise SystemExit("Import already recorded; refusing to overwrite adapted source files.")
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    with args.archive.open("rb") as stream:
        archive_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    entries = []
    with zipfile.ZipFile(args.archive) as archive:
        if archive.comment.decode("ascii").strip() != lock["main"]["commit"]:
            raise SystemExit("Archive Git commit differs from the approved source lock.")
        for entry in archive.infolist():
            if entry.is_dir() or not entry.filename.startswith(PREFIXES):
                continue
            relative = PurePosixPath(entry.filename)
            if relative.is_absolute() or ".." in relative.parts:
                raise SystemExit(f"Invalid archive path: {entry.filename}")
            target = ROOT.joinpath(*relative.parts)
            data = archive.read(entry)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            entries.append({"path": relative.as_posix(), "sha256": hashlib.sha256(data).hexdigest()})
    MANIFEST.write_text(json.dumps({
        "source_commit": lock["main"]["commit"],
        "archive_sha256": archive_hash,
        "imported_files": len(entries),
        "status": "source imported; scope cleanup and API adaptation pending",
        "files": entries,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(entries)} files from {lock['main']['commit']}; target build and docs preserved.")


if __name__ == "__main__":
    main()
