"""Compile the approved recipe plan into Forge 1.20.1 builtin server datapacks.

Run with --check to verify generated resources without changing the workspace.
The standalone mod resources are only read; all output stays under recipe_profiles.
"""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / "scripts/gt-recipe-plan.json"
OUTPUT = ROOT / "src/main/resources/recipe_profiles"
PACKS = ("gtm", "gtl", "gt_legacy", "gt_modern")


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def predicate(sized: dict) -> dict:
    return {key: sized[key] for key in ("item", "tag") if key in sized}


def recipe_path(recipe_id: str) -> str:
    namespace, path = recipe_id.split(":", 1)
    return f"data/{namespace}/recipes/{path}.json"


def dependencies(row: dict, profile: str) -> list:
    conditions = [{"type": "forge:mod_loaded", "modid": "gtceu"}]
    if profile == "gtl":
        conditions.append({"type": "forge:mod_loaded", "modid": "gtlcore"})
    conditions.extend(copy.deepcopy(c) for c in row.get("conditions", []) if "proposal_profile" not in c)
    return conditions


def workstation(row: dict, profile: str) -> dict:
    result = {
        "type": "neoecoae:integrated_working_station",
        "conditions": dependencies(row, profile),
        "inputItems": copy.deepcopy(row["item_inputs"]),
        "energy": row["ae_energy"],
        "processingTicks": row["ticks"],
    }
    assert 0 < len(result["inputItems"]) <= 9
    for source, target in (("item_output", "itemOutput"), ("fluid_output", "fluidOutput"),
                           ("input_fluid", "inputFluid")):
        if source in row:
            result[target] = copy.deepcopy(row[source])
    return result


def gt_recipe(row: dict, modern: bool) -> dict:
    def key(name: str) -> str:
        return f"gtceu:{name}" if modern else name

    def sized(item: dict) -> dict:
        return {"content": {"type": "gtceu:sized", "count": item["count"], "ingredient": predicate(item)}}

    result = {
        "type": row["recipe_type"],
        "conditions": [{"type": "forge:mod_loaded", "modid": "gtceu"}],
        "duration": row["ticks"],
        "inputs": {key("item"): [sized(item) for item in row["item_inputs"]]},
        "outputs": {key("item"): [sized(row["item_output"])]},
        "tickInputs": {key("eu"): [{"content": {"voltage": row["eu_per_tick"], "amperage": 1}
                                    if modern else row["eu_per_tick"]}]},
    }
    if "input_fluid" in row:
        fluid = row["input_fluid"]
        result["inputs"][key("fluid")] = [{"content": {
            "amount": fluid["amount"], "value": {"fluid": fluid["fluid"]}}}]
    if "blast_temperature" in row:
        result["data"] = {"ebf_temp": row["blast_temperature"]}
    if row.get("conditions"):
        assert all(set(c) == {"cleanroom"} for c in row["conditions"])
        result["recipeConditions"] = [{"type": key("cleanroom"), "cleanroom": c["cleanroom"]}
                                      for c in row["conditions"]]
    return result


def rebind(value, replacements: dict):
    if isinstance(value, dict):
        return {k: replacements.get(v, v) if k == "tag" and isinstance(v, str)
                else rebind(v, replacements) for k, v in value.items()}
    if isinstance(value, list):
        return [rebind(v, replacements) for v in value]
    return value


def compile_packs() -> dict[str, dict[str, dict]]:
    plan = read_json(PLAN)
    originals = {}
    for directory in ("src/generated/resources", "src/main/resources"):
        root = ROOT / directory / "data/neoecoae/recipes"
        if root.exists():
            originals.update({"neoecoae:" + f.relative_to(root).as_posix()[:-5]: read_json(f)
                              for f in sorted(root.rglob("*.json"))})
    native_ids = {r["replaces"] for r in plan["native_workstation_recipes"]}
    actual_native = {k for k, v in originals.items() if v.get("type") == "neoecoae:integrated_working_station"}
    # The approved 42-recipe plan predates the separately retained MEGACells resource family.
    retained_native = {k for k in actual_native if k.startswith("neoecoae:compat/megacells/")}
    assert native_ids == actual_native - retained_native, (
        f"Workstation coverage changed: {native_ids ^ (actual_native - retained_native)}")
    disabled = set(plan["disable_old_recipe_ids_when_profile_active"])
    assert disabled <= originals.keys(), f"Missing old recipes: {disabled - originals.keys()}"
    packs = {name: {"pack.mcmeta": {"pack": {"pack_format": 15,
             "description": f"NeoECOAE {name} progression recipes"}}} for name in PACKS}

    for profile in ("gtm", "gtl"):
        pack = packs[profile]
        for recipe_id in disabled:
            # Forge's empty conditional recipe explicitly removes an overridden recipe.
            pack[recipe_path(recipe_id)] = {"type": "forge:conditional", "recipes": []}
        for row in plan["native_workstation_recipes"]:
            pack[recipe_path(row["replaces"])] = workstation(row, profile)
        for row in plan["module_recipes"]:
            card = row["item_output"]["item"].split("integrated_working_station_module_", 1)[1]
            pack[recipe_path(f"neoecoae:integrated_working_station/module_{card}")] = workstation(row, profile)
        for row in plan["finished_material_conversions"]:
            material = row["item_inputs"][0]["item"].split(":", 1)[1]
            assert row["item_inputs"][0]["count"] == row["item_output"]["count"] == 1
            pack[recipe_path(f"neoecoae:compat/gt/materials/{material}")] = {
                "type": "minecraft:crafting_shapeless", "conditions": dependencies({}, profile),
                "ingredients": [predicate(row["item_inputs"][0])], "result": row["item_output"]}
        for row in plan["addon_recipe_adjustments"]:
            adjusted = copy.deepcopy(originals[row["recipe_id"]])
            assert adjusted["input_items"] == row["item_inputs_source"]
            assert adjusted["energy"] == row["ae_energy"]
            assert adjusted["output"]["#"] == row["item_output"]["count"]
            adjusted["conditions"] = dependencies(row, profile)
            adjusted["fluid"] = {"fluidStack": {"FluidName": row["input_fluid"]["fluid"],
                                                "Amount": row["input_fluid"]["amount"]}}
            pack[recipe_path(row["recipe_id"])] = adjusted
        for recipe_id, original in originals.items():
            if recipe_path(recipe_id) in pack:
                continue
            adjusted = rebind(original, plan["local_input_rebindings"])
            if adjusted != original:
                adjusted["conditions"] = dependencies(adjusted, profile)
                pack[recipe_path(recipe_id)] = adjusted
        for tag_id, members in plan["private_material_tags"].items():
            namespace, path = tag_id.split(":", 1)
            pack[f"data/{namespace}/tags/items/{path}.json"] = {
                "replace": False,
                "values": [{"id": item, "required": False} for item in members.get("item_ids", [])]
                          + ["#" + tag for tag in members.get("tag_ids", [])]}
        for metal, ids in plan["canonical_pure_metal_packing"].items():
            local = "aluminum" if metal == "aluminium" else metal
            ingots = ("neoecoae:compat/gt/aluminium_ingots" if metal == "aluminium"
                      else "forge:ingots/tungsten")
            blocks = ("neoecoae:compat/gt/aluminium_blocks" if metal == "aluminium"
                      else "forge:storage_blocks/tungsten")
            assert ids["ingots_per_block"] == 9
            pack[recipe_path(f"neoecoae:{local}_block")] = {
                "type": "minecraft:crafting_shaped", "conditions": dependencies({}, profile),
                "pattern": ["AAA", "AAA", "AAA"], "key": {"A": {"tag": ingots}},
                "result": {"item": ids["block"], "count": 1}}
            pack[recipe_path(f"neoecoae:{local}_ingot")] = {
                "type": "minecraft:crafting_shapeless", "conditions": dependencies({}, profile),
                "ingredients": [{"tag": blocks}], "result": {"item": ids["ingot"], "count": 9}}

    for section, directory in (("gt_bootstrap_recipes", "bootstrap"), ("ore_processing_recipes", "ore_processing")):
        for row in plan[section]:
            result_name = row["item_output"]["item"].split(":", 1)[1]
            path = recipe_path(f"neoecoae:compat/gt/{directory}/{row['key'].lower()}_{result_name}")
            for schema in ("legacy", "modern"):
                packs[f"gt_{schema}"][path] = gt_recipe(row, schema == "modern")
    return packs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    packs = compile_packs()
    failures = []
    for name, files in packs.items():
        root = OUTPUT / name
        actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()} if root.exists() else set()
        unexpected = actual - files.keys()
        if unexpected:
            failures.extend(f"Unexpected file: {name}/{path}" for path in sorted(unexpected))
        for relative, data in sorted(files.items()):
            path = root / relative
            text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
            if args.check:
                if not path.is_file() or path.read_text(encoding="utf-8") != text:
                    failures.append(f"Out of date: {name}/{relative}")
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")
        print(f"{name}: {len(files) - 1} resources")
    if failures:
        print("\n".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
