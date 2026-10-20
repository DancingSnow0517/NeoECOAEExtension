"""Migrate the locked 1.21 data tree to Forge 1.20, with a reviewable path/hash manifest.

Run --check first. Unsupported data components or output collisions abort before writes.
Only source/generated data resources are touched; audit evidence is never rewritten.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "porting/data-resource-conversion.json"
DIRECTORIES = {"recipe": "recipes", "advancement": "advancements", "loot_table": "loot_tables", "neoforge": "forge"}
TAG_DIRECTORIES = {"item": "items", "block": "blocks", "fluid": "fluids", "entity_type": "entity_types", "game_event": "game_events"}
EXCLUDED_TYPES = {"mekanism:crushing", "mekanism:enriching", "ars_nouveau:enchanting_apparatus"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def common_id(value: str) -> str:
    marker = "#" if value.startswith("#") else ""
    raw = value.removeprefix("#")
    if raw.startswith("c:"):
        raw = "forge:" + raw[2:]
        raw = raw.replace("forge:glass_blocks", "forge:glass")
    if raw.startswith("extendedae:"):
        raw = "expatternprovider:" + raw.split(":", 1)[1]
    if raw.startswith("neoforge:"):
        raw = "forge:" + raw.split(":", 1)[1]
    return marker + raw


def convert_tree(node):
    if isinstance(node, str):
        return common_id(node)
    if isinstance(node, list):
        return [convert_tree(value) for value in node]
    if not isinstance(node, dict):
        return node
    if "components" in node:
        raise ValueError("Item/fluid data components require a named NBT conversion")
    result = {}
    for key, value in node.items():
        target_key = "conditions" if key == "neoforge:conditions" else key
        if target_key in result:
            raise ValueError(f"Duplicate converted JSON key {target_key}")
        result[target_key] = convert_tree(value)
    if result.get("modid") == "extendedae":
        result["modid"] = "expatternprovider"
    # 1.21 ItemStack's JSON field is id; generic AE2 key tags also use id and must retain it.
    if "id" in result and "#t" not in result and ("count" in result or len(result) == 1):
        result["item"] = result.pop("id")
    if "id" in result and "amount" in result:
        result["fluid"] = result.pop("id")
    return result


def transform(path: Path, original: dict) -> tuple[dict | None, str]:
    kind = original.get("type", "")
    if kind in EXCLUDED_TYPES:
        return None, "excluded addon recipe"
    if kind == "extendedae:crystal_fixer":
        return None, "1.21 custom crystal-fixer recipe serializer is absent in ExtendedAE 1.20"
    if path.stem in {"sodium", "water_with_steam"} and kind == "neoecoae:cooling":
        return None, "Mekanism-only coolant contract is outside the frozen dependency scope"
    data = convert_tree(original)
    reason = "1.20 data directories, Forge conditions, common tags and stack JSON"
    if kind == "extendedae:crystal_assembler":
        data = {
            "conditions": data.get("conditions", []),
            "type": "neoecoae:extended_crystal_assembler",
            "inputItems": [dict(entry["ingredient"], count=entry.get("amount", 1)) for entry in data["input_items"]],
            "itemOutput": data["output"], "energy": 2000,
        }
        reason = "ECO EAE assembler counterpart; main material contract and 2000 AE retained"
    if kind == "extendedae:circuit_cutter":
        data["item_input"] = data.pop("input")
        reason = "actual ExtendedAE 1.20 circuit-cutter serializer fields"
    if kind == "advanced_ae:reaction":
        fluid = data.pop("input_fluid")
        predicate = fluid["ingredient"]
        if predicate != {"tag": "minecraft:water"}:
            raise ValueError("Review non-water AdvancedAE fluid conversion")
        data["fluid"] = {"fluid": "minecraft:water", "amount": fluid["amount"]}
        data["energy"] = data.pop("input_energy")
        reason = "actual AdvancedAE 1.20 fluid/energy serializer fields"
    if kind == "neoecoae:cooling" and path.stem == "water":
        data.pop("conditions", None)
        reason = "base water coolant remains available without out-of-scope Mekanism variants"
    if path.stem == "integrated_working_station_module_extendedae":
        substitutions = {
            "expatternprovider:crystal_assembler": "expatternprovider:ex_molecular_assembler",
            "expatternprovider:entro_ingot": "neoecoae:energized_superconductive_ingot",
            "expatternprovider:concurrent_processor": "neoecoae:superconducting_processor",
        }
        for entry in data["inputItems"]:
            if entry.get("item") in substitutions:
                entry["item"] = substitutions[entry["item"]]
        reason = "EAE card uses available 1.20 assembler and ECO materials; amounts and energy retained"
    return data, reason


def destination(path: Path) -> Path:
    parts = list(path.relative_to(ROOT).parts)
    if len(parts) > 5 and parts[4] == "c":
        parts[4] = "forge"
    if len(parts) > 5:
        parts[5] = DIRECTORIES.get(parts[5], parts[5])
    if len(parts) > 6 and parts[5] == "tags":
        parts[6] = TAG_DIRECTORIES.get(parts[6], parts[6])
    return ROOT.joinpath(*parts)


def plan() -> list[dict]:
    entries = []
    targets = {}
    roots = (ROOT / "src/main/resources/data", ROOT / "src/generated/resources/data")
    for base in roots:
        for path in sorted(base.rglob("*.json")):
            before = path.read_bytes()
            data, reason = transform(path, json.loads(before.decode("utf-8-sig")))
            target = destination(path)
            after = None if data is None else (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
            if after is not None:
                if target in targets:
                    raise ValueError(f"Resource collision: {path} and {targets[target]}")
                targets[target] = path
                if target != path and target.exists():
                    raise ValueError(f"Destination already exists: {target}")
            entries.append({"source": path.relative_to(ROOT).as_posix(), "source_sha256": sha(before),
                "target": None if after is None else target.relative_to(ROOT).as_posix(),
                "target_sha256": None if after is None else sha(after), "reason": reason, "_bytes": after})
    return entries


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if MANIFEST.exists():
        raise SystemExit("Resource conversion is already recorded; review existing manifest before any rerun")
    entries = plan()
    print(json.dumps({"inputs": len(entries), "removed": sum(e["target"] is None for e in entries),
        "moved": sum(e["target"] is not None and e["target"] != e["source"] for e in entries),
        "mode": "check only" if args.check else "apply"}, indent=2))
    if args.check:
        return
    for entry in entries:
        source = ROOT / entry["source"]
        if entry["target"] is not None:
            target = ROOT / entry["target"]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(entry["_bytes"])
        if entry["target"] != entry["source"]:
            source.unlink()
        del entry["_bytes"]
    manifest = {"status": "source conversion only; actual datagen/reload and runtime acceptance remain",
        "entries": entries}
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
