"""Read-only storage audit; optional repairs are exported to a new directory, never to the world.

Requires nbtlib. A header can be recovered only when the current file has exactly
format/entries, and a matching 20.4.2 backup proves identical typed inventory tags.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import uuid

import nbtlib

NBT_ERRORS = (OSError, ValueError, KeyError, TypeError, EOFError, struct.error)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical(tag):
    if isinstance(tag, nbtlib.Compound):
        return (tag.tag_id, tuple((key, canonical(value)) for key, value in sorted(tag.items())))
    if isinstance(tag, nbtlib.List):
        return (tag.tag_id, tag.subtype.tag_id, tuple(canonical(value) for value in tag))
    buffer = io.BytesIO()
    tag.write(buffer)
    return (tag.tag_id, buffer.getvalue())


def tag_uuid(tag):
    if not isinstance(tag, nbtlib.IntArray) or len(tag) != 4:
        raise ValueError("UUID is not a four-int array")
    return uuid.UUID(bytes=b"".join((int(value) & 0xFFFFFFFF).to_bytes(4, "big") for value in tag))


def read(path):
    root = nbtlib.load(path)
    if not isinstance(root.get("data"), nbtlib.Compound):
        raise ValueError("Missing SavedData data compound")
    return root, root["data"]


def snapshot_paths(data_dir):
    for path in sorted(data_dir.iterdir()):
        if path.name not in ("neoecoae_cells", "neoecoae_infinite") and not path.name.startswith("neoecoae_infinite_"):
            continue
        if path.is_dir():
            yield from sorted(child for child in path.rglob("*") if child.is_file())
        elif path.is_file():
            yield path


def validate_cell(data, identity):
    version = data.get("format")
    if not isinstance(version, nbtlib.Int) or int(version) not in (1, 2):
        raise ValueError("Unsupported finite snapshot format")
    identity_key = "cell" if "cell" in data and "id" not in data else "id"
    if tag_uuid(data.get(identity_key)) != identity:
        raise ValueError("Cell identity differs from filename")
    if identity_key == "cell" and (int(version) != 1 or not isinstance(data.get("revision"), nbtlib.Long)
                                   or int(data["revision"]) < 0):
        raise ValueError("Missing or invalid legacy revision")
    entries = data.get("entries")
    if not isinstance(entries, nbtlib.List) or entries and entries.subtype != nbtlib.Compound:
        raise ValueError("Invalid finite inventory list")
    seen = set()
    for entry in entries:
        key, amount = entry.get("key"), entry.get("amount")
        if not isinstance(key, nbtlib.Compound) or canonical(key) in seen:
            raise ValueError("Missing or duplicate resource key")
        seen.add(canonical(key))
        if identity_key == "cell" and (not isinstance(amount, nbtlib.Long) or int(amount) <= 0):
            raise ValueError("Invalid legacy resource amount")
    return identity_key


def repair_candidate(path, identity, current):
    if set(current) != {"format", "entries"} or not isinstance(current.get("format"), nbtlib.Int) \
            or int(current["format"]) != 1:
        raise ValueError("Snapshot is not the known header-only damage shape")
    backup = path.with_name(path.name + ".20.4.2.bak")
    _, preserved = read(backup)
    if set(preserved) != {"format", "entries", "cell", "revision"}:
        raise ValueError("Backup has additional state requiring a separate reconciliation")
    validate_cell(preserved, identity)
    if canonical(current["entries"]) != canonical(preserved["entries"]):
        raise ValueError("Current and backup inventories differ; automatic rollback is forbidden")
    return backup


def audit(world):
    world = world.resolve(strict=True)
    data_dir = world / "data"
    report = {"generated_at": datetime.now(timezone.utc).isoformat(), "world": str(world),
              "source_modified": False, "cells": [], "domains": []}
    report["source_files"] = [{"file": path.relative_to(data_dir).as_posix(), "sha256": digest(path)}
                              for path in snapshot_paths(data_dir)]
    for path in sorted((data_dir / "neoecoae_cells").glob("*.dat")):
        case = {"file": path.relative_to(data_dir).as_posix(), "sha256": digest(path), "status": "blocked"}
        try:
            identity = uuid.UUID(path.stem)
            _, data = read(path)
            case["entries"] = len(data.get("entries", []))
            try:
                identity_key = validate_cell(data, identity)
                case["status"] = "legacy" if identity_key == "cell" else "current"
            except NBT_ERRORS as error:
                case["failure"] = str(error)
                try:
                    backup = repair_candidate(path, identity, data)
                    case.update(status="header_repair_available", backup=backup.relative_to(data_dir).as_posix(),
                                backup_sha256=digest(backup), proof="typed_inventory_equal; UUID_and_revision_validated")
                except NBT_ERRORS as rejected:
                    case["repair_rejected"] = str(rejected)
        except NBT_ERRORS as error:
            case["failure"] = f"{type(error).__name__}: {error}"
        report["cells"].append(case)
    domain_paths = sorted((data_dir / "neoecoae_infinite").glob("domain_*.dat"))
    domain_paths += sorted(data_dir.glob("neoecoae_infinite_*_v3.dat"))
    for path in domain_paths:
        case = {"file": path.relative_to(data_dir).as_posix(), "sha256": digest(path), "status": "blocked"}
        try:
            root, data = read(path)
            case.update(status="requires_runtime_validation", fields=sorted(data), entries=len(data.get("entries", [])),
                        record_encoding=int(root.get("neoecoae_record_encoding", 0)),
                        sidecars=[suffix for suffix in (".delta.dat", ".commit") if path.with_name(path.name + suffix).exists()])
        except NBT_ERRORS as error:
            case["failure"] = f"{type(error).__name__}: {error}"
        report["domains"].append(case)
    report["summary"] = {
        "cells": len(report["cells"]),
        "valid_cell_headers": sum(case["status"] in ("legacy", "current") for case in report["cells"]),
        "header_repairs_available": sum(case["status"] == "header_repair_available" for case in report["cells"]),
        "blocked_cells": sum(case["status"] == "blocked" for case in report["cells"]),
        "domains": len(report["domains"]),
        "unreadable_domain_files": sum(case["status"] == "blocked" for case in report["domains"]),
    }
    return report


def prepare(world, output, report):
    world, output = world.resolve(strict=True), output.resolve()
    if output == world or world in output.parents:
        raise ValueError("Repair output must be outside the source world")
    # A new directory is required; existing prepared files are never overwritten.
    output.mkdir(parents=True, exist_ok=False)
    source = world / "data"
    originals, prepared = output / "originals" / "data", output / "prepared" / "data"
    originals.mkdir(parents=True)
    for path in source.iterdir():
        if path.name not in ("neoecoae_cells", "neoecoae_infinite") and not path.name.startswith("neoecoae_infinite_"):
            continue
        if path.is_dir():
            shutil.copytree(path, originals / path.name)
        elif path.is_file():
            shutil.copyfile(path, originals / path.name)
    shutil.copytree(originals, prepared)
    for entry in report["source_files"]:
        for base in (source, originals, prepared):
            if digest(base / entry["file"]) != entry["sha256"]:
                raise ValueError("Source or snapshot sidecar changed during export")
    for case in report["cells"]:
        target = prepared / case["file"]
        if digest(source / case["file"]) != case["sha256"] or digest(target) != case["sha256"]:
            raise ValueError("Source changed during snapshot export")
        if case["status"] == "header_repair_available":
            backup = originals / case["backup"]
            if digest(backup) != case["backup_sha256"]:
                raise ValueError("Backup changed during snapshot export")
            # Recheck the exported pair before using it, including typed inventory equality.
            _, damaged = read(target)
            candidate = repair_candidate(target, uuid.UUID(target.stem), damaged)
            shutil.copyfile(candidate, target)
            case["prepared_sha256"] = digest(target)
    for case in report["domains"]:
        if digest(source / case["file"]) != case["sha256"] or digest(prepared / case["file"]) != case["sha256"]:
            raise ValueError("Domain changed during snapshot export")
    report["prepared_directory"] = str(output)
    (output / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("--report", type=Path, help="Write a JSON audit without modifying the source world")
    parser.add_argument("--prepare", type=Path, help="Export originals and independently prepared replacements to a NEW directory")
    args = parser.parse_args()
    report = audit(args.world)
    if args.prepare:
        prepare(args.world, args.prepare, report)
    if args.report:
        target = args.report.resolve()
        world = args.world.resolve()
        if target == world or world in target.parents:
            raise ValueError("Audit report must be outside the source world")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
