"""Restore air-converted Omni disk IDs from matching backup drives into a NEW world copy only."""

from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import io
import json
from pathlib import Path
import shutil
import zlib
from uuid import UUID

import nbtlib

DISK_IDS = {
    f"neoecoae:eco_{family}_cell_{size}"
    for family in ("omni", "complex_omni", "quantum_omni")
    for size in ("16m", "64m", "256m")
}
UUID_TAG = "ae_universal_cell_uuid"


def chunks(path: Path):
    data = path.read_bytes()
    if len(data) < 8192:
        raise ValueError(f"Truncated region: {path}")
    for slot in range(1024):
        location = int.from_bytes(data[slot * 4:slot * 4 + 4], "big")
        if not location:
            continue
        offset = (location >> 8) * 4096
        length = int.from_bytes(data[offset:offset + 4], "big")
        payload = data[offset + 5:offset + 4 + length]
        codec = data[offset + 4]
        if codec == 2:
            payload = zlib.decompress(payload)
        elif codec == 1:
            payload = gzip.decompress(payload)
        elif codec != 3:
            raise ValueError(f"Unsupported region compression {codec}: {path}")
        yield slot, nbtlib.File.parse(io.BytesIO(payload))


def region_files(world: Path):
    return sorted(path for path in world.rglob("*.mca") if path.parent.name == "region")


def disk_id(drive):
    return str(drive.get("cellStack", {}).get("id", ""))


def disk_uuid(drive):
    return str(drive.get("cellStack", {}).get("tag", {}).get(UUID_TAG, ""))


def position(drive):
    return tuple(int(drive[key]) for key in ("x", "y", "z"))


def fingerprint(world: Path):
    return {
        path.relative_to(world).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(world.rglob("*"))
        if path.is_file() and path.name != "session.lock"
    }


def plan(world: Path, reference: Path):
    originals = {}
    for path in region_files(reference):
        dimension = path.parent.relative_to(reference).as_posix()
        for _, chunk in chunks(path):
            for drive in chunk.get("block_entities", []):
                if str(drive.get("id", "")) == "neoecoae:eco_drive" and disk_id(drive) in DISK_IDS:
                    identity = (dimension, position(drive), disk_uuid(drive))
                    if not identity[2] or identity in originals:
                        raise ValueError("Backup disk identity is missing or ambiguous")
                    if str(UUID(identity[2])) != identity[2]:
                        raise ValueError("Backup UUID must be canonical")
                    originals[identity] = drive
    result = []
    for path in region_files(world):
        dimension = path.parent.relative_to(world).as_posix()
        for slot, chunk in chunks(path):
            for drive in chunk.get("block_entities", []):
                if str(drive.get("id", "")) != "neoecoae:eco_drive" or disk_id(drive) != "minecraft:air":
                    continue
                uuid = disk_uuid(drive)
                if not uuid:
                    continue
                if str(UUID(uuid)) != uuid:
                    raise ValueError("Current disk UUID must be canonical")
                original = originals.get((dimension, position(drive), uuid))
                if original is None:
                    raise ValueError(f"No exact backup match for air-converted disk {uuid} at {position(drive)}")
                if int(drive["cellStack"].get("Count", 0)) != 1 or int(original["cellStack"].get("Count", 0)) != 1:
                    raise ValueError("Unexpected disk stack size")
                # The live/current world's inventory remains authoritative. Only the item ID is recovered.
                if not (world / "data/ae_universal_cell_data" / f"{uuid}.dat").is_file():
                    raise ValueError(f"Missing current Omni inventory for {uuid}; refusing to synthesize contents")
                result.append({"region": path.relative_to(world).as_posix(), "slot": slot,
                               "position": list(position(drive)), "uuid": uuid,
                               "restored_id": disk_id(original)})
    if not result:
        raise ValueError("No air-converted Omni drives with matching backup identities found")
    return result


def write_chunk(path: Path, slot: int, chunk):
    buffer = io.BytesIO()
    chunk.write(buffer)
    payload = b"\x02" + zlib.compress(buffer.getvalue())
    encoded = len(payload).to_bytes(4, "big") + payload
    sectors = (len(encoded) + 4095) // 4096
    if sectors > 255:
        raise ValueError("Recovered chunk exceeds region sector limit")
    # Append in the new copy so all other chunks retain their original compressed bytes.
    with path.open("r+b") as stream:
        stream.seek(0, 2)
        end = stream.tell()
        offset = (end + 4095) // 4096
        stream.write(b"\0" * (offset * 4096 - end))
        stream.write(encoded + b"\0" * (sectors * 4096 - len(encoded)))
        stream.seek(slot * 4)
        stream.write(((offset << 8) | sectors).to_bytes(4, "big"))


def prepare(world: Path, reference: Path, output: Path):
    world, reference, output = world.resolve(), reference.resolve(), output.resolve()
    if any(output == source or output.is_relative_to(source) or source.is_relative_to(output)
           for source in (world, reference)):
        raise ValueError("Output must be outside both source worlds")
    if output.exists():
        raise ValueError("Output must be a new directory")
    before, reference_before = fingerprint(world), fingerprint(reference)
    changes = plan(world, reference)
    shutil.copytree(world, output, ignore=shutil.ignore_patterns("session.lock"))
    if fingerprint(world) != before or fingerprint(reference) != reference_before or fingerprint(output) != before:
        raise ValueError("Source changed during copy; discard this incomplete output and retry with a stable snapshot")
    grouped = {}
    for change in changes:
        grouped.setdefault((change["region"], change["slot"]), []).append(change)
    fixtures = output / "omni-drive-fixtures"
    fixtures.mkdir()
    for (relative, slot), fixes in grouped.items():
        chunk = next(chunk for index, chunk in chunks(output / relative) if index == slot)
        for fix in fixes:
            drive = next(be for be in chunk["block_entities"] if position(be) == tuple(fix["position"]))
            prior = copy.deepcopy(drive)
            drive["cellStack"]["id"] = nbtlib.String(fix["restored_id"])
            restored = copy.deepcopy(drive)
            restored["cellStack"]["id"] = prior["cellStack"]["id"]
            if restored != prior:
                raise ValueError("Recovery modified data other than the item ID")
            fix["fixture"] = f"omni-drive-fixtures/{fix['uuid']}.dat"
            nbtlib.File(copy.deepcopy(drive)).save(output / fix["fixture"], gzipped=True)
        write_chunk(output / relative, slot, chunk)
    if fingerprint(world) != before or fingerprint(reference) != reference_before:
        raise ValueError("Source changed during recovery; output requires a fresh stable snapshot")
    report = {"source": str(world), "reference": str(reference), "output": str(output),
              "restored_drives": changes, "original_files_changed": [],
              "source_sha256": before, "reference_sha256": reference_before}
    (output / "omni-recovery-manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("--output", type=Path, help="New full world copy; omitting this performs a read-only audit")
    args = parser.parse_args()
    report = prepare(args.world, args.reference, args.output) if args.output else {
        "restored_drives": plan(args.world.resolve(), args.reference.resolve())}
    print(json.dumps({"restored_disks": len(report["restored_drives"]), "output": report.get("output"),
                      "original_files_changed": []}, indent=2))


if __name__ == "__main__":
    main()
