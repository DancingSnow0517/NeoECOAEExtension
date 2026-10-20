import unittest
from pathlib import Path
import convert_data_resources as migration


class ResourceConversionTest(unittest.TestCase):
    def test_live_lightning_disassembly_cannot_multiply_components_or_return_new_consumables(self):
        import json
        for size, amount in (("16m", 1), ("64m", 2), ("256m", 4)):
            path = migration.ROOT / f"src/main/resources/data/neoecoae/recipes/compat/ae2lt/disassembly_eco_lightning_cell_{size}.json"
            data = json.loads(path.read_text(encoding="utf-8"))
            returned = {item["item"]: item.get("count", 1) for item in data["cell_disassembly_items"]}
            self.assertEqual({"neoecoae:eco_lightning_cell_housing": 1, f"neoecoae:eco_cell_component_{size}": 1,
                              "ae2lt:lightning_cell_component_v": amount}, returned)

    def test_live_resource_manifest_and_material_ledgers_match(self):
        import verify_data_resources
        report = verify_data_resources.verify()
        self.assertEqual([], report["errors"])
        self.assertEqual(9, report["disassembly_contracts"])

    def test_paths_follow_120_names(self):
        cases = {
            "src/main/resources/data/neoecoae/recipe/test.json": "src/main/resources/data/neoecoae/recipes/test.json",
            "src/generated/resources/data/c/tags/item/ingots/test.json": "src/generated/resources/data/forge/tags/items/ingots/test.json",
            "src/generated/resources/data/neoecoae/neoforge/biome_modifier/test.json": "src/generated/resources/data/neoecoae/forge/biome_modifier/test.json",
        }
        for before, after in cases.items():
            self.assertEqual(migration.ROOT / after, migration.destination(migration.ROOT / before))

    def test_conditions_and_stack_shapes_do_not_confuse_ae_keys(self):
        original = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "extendedae"}],
                    "result": {"id": "minecraft:diamond", "count": 1024},
                    "generic": {"id": "minecraft:diamond", "#t": "ae2:i", "#": 1024},
                    "tag": "#c:dusts/iron"}
        converted = migration.convert_tree(original)
        self.assertEqual("forge:mod_loaded", converted["conditions"][0]["type"])
        self.assertEqual("expatternprovider", converted["conditions"][0]["modid"])
        self.assertEqual({"item": "minecraft:diamond", "count": 1024}, converted["result"])
        self.assertEqual(original["generic"], converted["generic"])
        self.assertEqual("#forge:dusts/iron", converted["tag"])
        self.assertIn("neoforge:conditions", original)

    def test_eae_counterpart_keeps_material_cost(self):
        original = {"type": "extendedae:crystal_assembler",
                    "input_items": [{"ingredient": {"item": "minecraft:iron_ingot"}, "amount": 4}],
                    "output": {"id": "minecraft:diamond", "count": 4}}
        converted, _ = migration.transform(Path("superconducting_processor.json"), original)
        self.assertEqual("neoecoae:extended_crystal_assembler", converted["type"])
        self.assertEqual([{"item": "minecraft:iron_ingot", "count": 4}], converted["inputItems"])
        self.assertEqual({"item": "minecraft:diamond", "count": 4}, converted["itemOutput"])
        self.assertEqual(2000, converted["energy"])

    def test_advanced_recipe_uses_actual_target_fields(self):
        original = {"type": "advanced_ae:reaction", "input_energy": 500000,
                    "input_fluid": {"ingredient": {"tag": "minecraft:water"}, "amount": 500}}
        converted, _ = migration.transform(Path("test.json"), original)
        self.assertEqual(500000, converted["energy"])
        self.assertEqual({"fluid": "minecraft:water", "amount": 500}, converted["fluid"])
        self.assertNotIn("input_energy", converted)

    def test_excluded_recipes_are_removed_and_unknown_components_rejected(self):
        self.assertIsNone(migration.transform(Path("test.json"), {"type": "mekanism:crushing"})[0])
        with self.assertRaises(ValueError):
            migration.convert_tree({"output": {"id": "minecraft:diamond", "components": {"unknown": True}}})

    def test_eae_upgrade_uses_available_materials_without_changing_amounts(self):
        original = {"inputItems": [{"item": "extendedae:crystal_assembler", "count": 8},
                                  {"item": "extendedae:entro_ingot", "count": 256},
                                  {"item": "extendedae:concurrent_processor", "count": 128}], "energy": 16384000}
        converted, _ = migration.transform(Path("integrated_working_station_module_extendedae.json"), original)
        self.assertEqual([8, 256, 128], [v["count"] for v in converted["inputItems"]])
        self.assertEqual("expatternprovider:ex_molecular_assembler", converted["inputItems"][0]["item"])
        self.assertEqual("neoecoae:energized_superconductive_ingot", converted["inputItems"][1]["item"])
        self.assertEqual(16384000, converted["energy"])


if __name__ == "__main__":
    unittest.main()
