"""Finish recipe-type ownership and remove positive dependencies outside the frozen addon set."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import argparse

ROOT = Path(__file__).resolve().parents[2]
ALLOWED = {"minecraft", "forge", "neoecoae", "ae2", "ldlib", "guideme", "registrate", "mixinextras",
           "advanced_ae", "expatternprovider", "extendedae_plus", "ae2lt", "thunderbolt", "geckolib", "glodium"}


def required_mods(node):
    if isinstance(node, list):
        return set().union(*(required_mods(value) for value in node)) if node else set()
    if not isinstance(node, dict):
        return set()
    if node.get("type") == "forge:not":
        return set()
    if node.get("type") == "forge:mod_loaded":
        return {node["modid"]}
    return set().union(*(required_mods(value) for value in node.values())) if node else set()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    manifest_path = ROOT / "porting/data-resource-conversion.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("recipe_scope_finished"):
        raise SystemExit("Data recipe followup already applied")
    removed = Counter()
    changed = 0
    removed_files = 0
    for entry in manifest["entries"]:
        if entry["target"] is None:
            continue
        path = ROOT / entry["target"]
        original = path.read_bytes()
        data = json.loads(original)
        forbidden = required_mods(data.get("conditions", [])) - ALLOWED
        reason = None
        if forbidden:
            removed_files += 1
            reason = "outside-scope addon data: " + ", ".join(sorted(forbidden))
            removed.update(forbidden)
            after = None
        elif data.get("type") == "ae2:storage_cell_disassembly":
            data["type"] = "neoecoae:storage_cell_disassembly"
            reason = "ECO-owned item-use disassembly metadata replaces unavailable AE2 19 recipe API"
            after = (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        else:
            continue
        changed += 1
        entry.setdefault("revisions", []).append({"target": entry["target"], "sha256": entry["target_sha256"], "reason": entry["reason"]})
        entry["reason"] = reason
        if after is None:
            entry["target"] = None
            entry["target_sha256"] = None
            if not args.check:
                path.unlink()
        else:
            entry["target_sha256"] = hashlib.sha256(after).hexdigest()
            if not args.check:
                path.write_bytes(after)
    print(json.dumps({"mode": "check" if args.check else "apply", "changed": changed,
        "removed_files": removed_files, "removed_by_mod": dict(removed)}, indent=2))
    if args.check:
        return
    manifest["recipe_scope_finished"] = True
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for relative in ("all/NEItems.java", "items/ECOStorageCellItem.java"):
        path = ROOT / "src/main/java/cn/dancingsnow/neoecoae" / relative
        text = path.read_text(encoding="utf-8").replace("import appeng.recipes.game.StorageCellDisassemblyRecipe;",
            "import cn.dancingsnow.neoecoae.recipe.StorageCellDisassemblyRecipe;")
        text = text.replace('prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);',
                            'recipe.save(prov, ctx.getId().withPrefix("disassembly/"));')
        path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
