"""Repair resource schemas rejected by the real 1.20.1 client; retain hash history."""
from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path

from convert_data_resources import convert_tree, target_recipe_fields

ROOT = Path(__file__).resolve().parents[2]
AE2 = Path(r"E:\Minecraft Project\Applied-Energistics-2")


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    manifest_path = ROOT / "porting/data-resource-conversion.json"
    manifest = json.loads(manifest_path.read_bytes())
    by_target = {entry["target"]: entry for entry in manifest["entries"] if entry["target"]}
    changes = []
    for path in sorted(ROOT.glob("src/*/resources/data/**/*.json")):
        before = path.read_bytes()
        original = json.loads(before)
        converted = convert_tree(original)
        converted = target_recipe_fields(converted)
        if converted.get("type") == "advanced_ae:reaction" and "fluidStack" not in converted["fluid"]:
            fluid = converted["fluid"]
            converted["fluid"] = {"fluidStack": {"FluidName": fluid["fluid"], "Amount": fluid["amount"]}}
        if converted == original:
            continue
        after = encoded(converted)
        relative = path.relative_to(ROOT).as_posix()
        entry = by_target[relative]
        if sha(before) != entry["target_sha256"]:
            raise ValueError(f"Unexpected unrecorded resource edit: {relative}")
        entry.setdefault("revisions", []).append({"target": relative, "sha256": sha(before),
                                                  "reason": entry["reason"]})
        entry["target_sha256"] = sha(after)
        entry["reason"] = ("1.20 advancement item arrays and separate tag predicates; criteria retained"
                           if "advancements" in path.parts else
                           "Native 1.20 recipe serializer fields and AE2 key channel; quantities and energy retained")
        path.write_bytes(after)
        changes.append({"path": relative, "before_sha256": sha(before), "after_sha256": sha(after)})
    manifest_path.write_bytes(encoded(manifest))

    for path in sorted(ROOT.glob("src/*/resources/assets/**/*.json")):
        before = path.read_bytes()
        after = before.replace(b'"neoforge:composite"', b'"forge:composite"')
        after = after.replace(b'"neoforge:fluid_container"', b'"forge:fluid_container"')
        after = after.replace(b'"neoforge:item/bucket_drip"', b'"forge:item/bucket"')
        after = after.replace(b'ae2:block/generics/', b'neoecoae:block/workstation/generics/')
        if after != before:
            path.write_bytes(after)
            changes.append({"path": path.relative_to(ROOT).as_posix(),
                            "before_sha256": sha(before), "after_sha256": sha(after)})

    # Preserve the intended workstation artwork. These AE2 textures did not exist in AE2 15.
    commit = subprocess.run(["git", "rev-parse", "origin/1.21.1"], cwd=AE2,
                            check=True, capture_output=True, text=True).stdout.strip()
    imported = []
    for name in ("back", "bottom", "side"):
        source = f"src/main/resources/assets/ae2/textures/block/generics/{name}.png"
        data = subprocess.run(["git", "show", f"{commit}:{source}"], cwd=AE2,
                              check=True, capture_output=True).stdout
        target = ROOT / f"src/main/resources/assets/neoecoae/textures/block/workstation/generics/{name}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        imported.append({"source": source, "target": target.relative_to(ROOT).as_posix(), "sha256": sha(data)})
    report_path = ROOT / "porting/runtime-resource-adaptation.json"
    previous = json.loads(report_path.read_bytes()) if report_path.exists() else {"changes": []}
    previous["changes"].extend(changes)
    previous.update({"ae2_artwork_source": "https://github.com/AppliedEnergistics/Applied-Energistics-2",
                     "ae2_artwork_commit": commit, "ae2_artwork_imports": imported})
    report_path.write_bytes(encoded(previous))
    print(json.dumps({"changed_resources": len(changes), "preserved_ae2_textures": len(imported)}))


if __name__ == "__main__":
    main()
