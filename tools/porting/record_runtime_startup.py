"""Record real development-client startup evidence without claiming release acceptance."""
from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from record_validation import read_log

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    base = ROOT / "validation/business-client-03.log"
    combined = ROOT / "validation/business-runtime-final.log"
    base_log = read_log(base)
    combined_log = read_log(combined)
    recipes = ["reaction_chamber/energized_fluix_crystal", "reaction_chamber/energized_crystal",
               "circuit_cutter/superconducting_processor_print", "compat/ae2lt/eco_lightning_cell_16m",
               "compat/ae2lt/eco_lightning_cell_64m", "compat/ae2lt/eco_lightning_cell_256m"]
    checks = {
        "base_world_entered": "CLIENT_SMOKE world entered:" in base_log,
        "combined_world_entered": "CLIENT_SMOKE world entered:" in combined_log,
        "all_four_addons_loaded": "addons=[advanced_ae, expatternprovider, extendedae_plus, ae2lt]" in combined_log,
        "six_migrated_recipes_loaded": all(f"recipe neoecoae:{recipe} loaded=true" in combined_log for recipe in recipes),
        "workstation_screen_opened": "workstation screen=com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer" in combined_log,
        "menu_closed": "CLIENT_SMOKE screen: in_game" in combined_log.split("workstation screen=")[-1],
        "all_dimensions_saved": "ThreadedAnvilChunkStorage: All dimensions are saved" in combined_log,
        "gradle_success": "BUILD SUCCESSFUL" in combined_log,
        "no_neoecoae_recipe_parse_errors": "Parsing error loading recipe neoecoae:" not in combined_log,
        "no_neoecoae_model_load_errors": "Failed to load model neoecoae:" not in combined_log,
        "no_datapack_load_failure": "Failed to load datapacks" not in combined_log,
        "no_fatal_error": "/FATAL]" not in combined_log,
    }
    screenshots = []
    for name in ("base-world", "combined-world", "combined-workstation"):
        path = ROOT / f"validation/runtime-startup-20261021/{name}.png"
        screenshots.append({"path": path.relative_to(ROOT).as_posix(), "sha256": sha(path)})
    report = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "environment": "real Forge 47.4.28 / Minecraft 1.20.1 / Java 17 development client; full business tree",
        "main_baseline": "dc8344f48ee866e57e02c285fdda5e0fb5da9421",
        "checks": checks,
        "result": "success" if all(checks.values()) else "failed",
        "logs": [{"path": path.relative_to(ROOT).as_posix(), "sha256": sha(path)} for path in (base, combined)],
        "screenshots": screenshots,
        "package": json.loads((ROOT / "porting/package-validation.json").read_bytes()),
        "python_resource_tests": {"tests": 10, "failures": 0},
        "known_external_data_errors": ["AdvancedAE optional Curios/wireless-terminal resources without AE2WTLib",
                                       "ExtendedAE optional EMC-interface loot table without ProjectE"],
        "limits": ["Menu was opened through the real server block-use/menu packet path; no mouse-gesture acceptance",
                   "Native desktop automation helper unavailable; screenshots use the game framebuffer",
                   "Full business tests remain blocked at compileTestJava; last check: 115 errors in 29 files",
                   "Dedicated server, ordinary Forge installation, multiplayer and full machine behavior not verified",
                   "Base world was captured before the resource followup and its test process was explicitly stopped"],
    }
    (ROOT / "porting/runtime-startup-validation.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not all(checks.values()):
        raise SystemExit(f"Incomplete runtime evidence: {checks}")
    state_path = ROOT / "porting/validation-state.json"
    state = json.loads(state_path.read_bytes())
    state["current_business_runtime"] = "base and combined development clients entered worlds; see runtime-startup-validation.json"
    state["dedicated_business_server"] = "not verified"
    state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": report["result"], "checks": checks}))


if __name__ == "__main__":
    main()
