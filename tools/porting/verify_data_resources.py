"""Verify the migrated data tree, hashes, scope, and assembly/disassembly material conservation."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def inputs(recipe: dict) -> Counter | None:
    kind = recipe.get("type")
    if kind == "minecraft:crafting_shapeless":
        if any(not isinstance(i, dict) or "item" not in i for i in recipe["ingredients"]):
            return None
        return Counter(i["item"] for i in recipe["ingredients"])
    if kind == "minecraft:crafting_shaped":
        result = Counter()
        for row in recipe["pattern"]:
            for char in row:
                if char == " ":
                    continue
                requirement = recipe["key"][char]
                if "item" not in requirement:
                    return None
                result[requirement["item"]] += 1
        return result
    if kind.startswith("ae2lt:") and "inputs" in recipe:
        result = Counter()
        for requirement in recipe["inputs"]:
            if "item" not in requirement["ingredient"]:
                return None
            result[requirement["ingredient"]["item"]] += requirement.get("count", 1)
        return result
    return None


def verify(fix_disassembly: bool = False) -> dict:
    manifest_path = ROOT / "porting/data-resource-conversion.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    documents = {}
    errors = []
    by_target = {entry["target"]: entry for entry in manifest["entries"] if entry["target"]}
    for base in (ROOT / "src/main/resources/data", ROOT / "src/generated/resources/data"):
        for path in base.rglob("*.json"):
            relative = path.relative_to(ROOT).as_posix()
            data = path.read_bytes()
            documents[relative] = json.loads(data)
            if relative not in by_target:
                errors.append(f"Untracked migrated data resource: {relative}")
            elif hashlib.sha256(data).hexdigest() != by_target[relative]["target_sha256"]:
                errors.append(f"Resource checksum mismatch: {relative}")
            if "neoforge:" in data.decode("utf-8"):
                errors.append(f"NeoForge resource marker remains: {relative}")
            parts = Path(relative).parts
            if parts[5] in {"recipe", "advancement", "loot_table", "neoforge"}:
                errors.append(f"1.21 resource directory remains: {relative}")
            if parts[5] == "tags" and parts[6] in {"item", "fluid", "block", "entity_type", "game_event"}:
                errors.append(f"1.21 tag directory remains: {relative}")
    for target in by_target:
        if target not in documents:
            errors.append(f"Missing converted resource: {target}")
    assembly = {}
    for relative, recipe in documents.items():
        if recipe.get("type") == "neoecoae:storage_cell_disassembly":
            continue
        outputs = recipe.get("results", [recipe.get("result", recipe.get("itemOutput"))])
        for output in outputs:
            output_id = output.get("item", output.get("id", "")) if isinstance(output, dict) else ""
            if output_id.startswith("neoecoae:eco_"):
                ledger = inputs(recipe)
                if ledger is not None and output.get("count", 1) == 1:
                    assembly.setdefault(output_id, []).append((relative, ledger))
    disassembly_count = 0
    fixed = []
    for relative, recipe in documents.items():
        if recipe.get("type") != "neoecoae:storage_cell_disassembly":
            continue
        disassembly_count += 1
        cell = recipe["cell"]
        candidates = assembly.get(cell, [])
        if not candidates:
            errors.append(f"No verifiable assembly for disassembly of {cell}")
            continue
        if fix_disassembly and cell.startswith("neoecoae:eco_lightning_cell_") and len(candidates) == 1:
            entry = by_target[relative]
            # The locked local import retains the original product set. Processing consumables are not added as new returns.
            source = json.loads(subprocess.check_output(["git", "show", "9eaf7de:" + entry["source"]], cwd=ROOT))
            expected = [{"item": item.get("item", item.get("id")),
                         "count": min(item.get("count", 1), candidates[0][1][item.get("item", item.get("id"))])}
                        for item in source["cell_disassembly_items"]]
            if any(item["count"] <= 0 for item in expected):
                errors.append(f"An original disassembly output is absent from assembly: {cell}")
                continue
            if recipe["cell_disassembly_items"] != expected:
                entry.setdefault("revisions", []).append({"target": relative, "sha256": entry["target_sha256"], "reason": entry["reason"]})
                recipe["cell_disassembly_items"] = expected
                payload = (json.dumps(recipe, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
                (ROOT / relative).write_bytes(payload)
                entry["target_sha256"] = hashlib.sha256(payload).hexdigest()
                entry["reason"] = "clamp original lightning-component return to actual assembly input; preserve the original product set"
                fixed.append(cell)
        returned = Counter()
        for item in recipe["cell_disassembly_items"]:
            returned[item["item"]] += item.get("count", 1)
        if any(any(amount > ledger[item] for item, amount in returned.items()) for _, ledger in candidates):
            errors.append(f"Disassembly creates materials: {cell}: returned {dict(returned)}, assembly {[(p, dict(v)) for p, v in candidates]}")
    if fixed:
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    result = {"data_files": len(documents), "removed_data_files": sum(not e["target"] for e in manifest["entries"]),
        "disassembly_contracts": disassembly_count, "corrected_lightning_contracts": fixed, "errors": errors,
        "scope": "static files and explicit material ledgers only; not datagen or runtime reload acceptance"}
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fix-disassembly", action="store_true")
    parser.add_argument("--output", default="porting/data-resource-validation.json")
    args = parser.parse_args()
    result = verify(args.fix_disassembly)
    (ROOT / args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(bool(result["errors"]))
