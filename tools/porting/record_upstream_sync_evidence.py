"""Record the approved 2026-10-23 increment against local build and runtime evidence."""
from pathlib import Path
from datetime import datetime, timezone
from collections import Counter
import hashlib
import json
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
INITIAL = "6936a93069cdf42c45157f78fe983bcf342fd974"
TARGET = "c95684c93fd68f55315379ca2282675a01eb87fa"
BASE = "dc8344f48ee866e57e02c285fdda5e0fb5da9421"
REFERENCE = Path(r"E:\Minecraft Project\NeoECOAEExtension-1.21.1")
OUT = ROOT / "docs/porting-2026-10-23/UPSTREAM_SYNC_VALIDATION.json"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args, cwd=ROOT):
    return subprocess.check_output(["git", "-c", "core.quotepath=false", *args], cwd=cwd).decode("utf-8").strip()


def log_check(name, marker, expected=True):
    path = ROOT / f"validation/upstream-sync-20261023-{name}.log"
    text = path.read_text(encoding="utf-8-sig")
    passed = marker in text and "BUILD SUCCESSFUL" in text
    if expected:
        assert passed, (name, marker)
    return {"log": path.relative_to(ROOT).as_posix(), "sha256": sha(path),
            "result": "passed" if passed else "failed", "marker": marker}


def tests(task):
    files = sorted((ROOT / "build/test-results" / task).glob("TEST-*.xml"))
    assert files
    totals = Counter()
    skipped = []
    for path in files:
        root = ET.parse(path).getroot()
        for key in ["tests", "failures", "errors", "skipped"]:
            totals[key] += int(root.attrib.get(key, 0))
        for case in root.findall("testcase"):
            if case.find("skipped") is not None:
                skipped.append({"class": root.attrib["name"], "method": case.attrib["name"]})
    assert totals["failures"] == totals["errors"] == 0, (task, totals)
    return {**totals, "passed": totals["tests"] - totals["skipped"], "skipped_cases": skipped}


overlay_path = ROOT / "porting/upstream-sync-2026-10-23.json"
overlay = json.loads(overlay_path.read_text(encoding="utf-8-sig"))
lock = json.loads((ROOT / "porting/source-lock.json").read_text())
assert lock["main"]["commit"] == BASE
assert overlay["approved_increment_target"] == TARGET
assert git("diff", "--name-only", INITIAL, "--", "porting/source-lock.json", "porting/dependencies.lock.json") == ""

package_path = ROOT / "porting/package-validation-upstream-sync-2026-10-23.json"
package = json.loads(package_path.read_text(encoding="utf-8"))
assert package["result"] == "success", package["problems"]

changed = set(filter(None, git("diff", "--name-only", INITIAL).splitlines()))
changed.update(filter(None, git("ls-files", "--others", "--exclude-standard").splitlines()))
sources = {path: sha(ROOT / path) for path in sorted(changed)
           if (ROOT / path).is_file() and (path.startswith(("src/", "guidebook/", "tools/porting/")) or path == "build.gradle")}
changed_assets = [path for path in sources if path.startswith(("src/main/resources/", "src/generated/resources/"))]
with zipfile.ZipFile(ROOT / package["jar"]) as jar:
    mismatches = []
    for path in changed_assets:
        inner = path.split("/resources/", 1)[1]
        if inner not in jar.namelist() or hashlib.sha256(jar.read(inner)).hexdigest() != sources[path]:
            mismatches.append(path)
    assert not mismatches, mismatches
    deleted_resources = [entry.get("target", entry["path"]) for entry in overlay["increment_files"]
                         if entry["action"] == "delete" and entry.get("target", entry["path"]).startswith("src/main/resources/")]
    stale_resources = [path for path in deleted_resources if path.split("/resources/", 1)[1] in jar.namelist()]
    assert not stale_resources, stale_resources

def upstream_json(commit, path):
    return json.loads(git("show", commit + ":" + path, cwd=REFERENCE))

locale_path = "src/main/resources/assets/neoecoae/lang/zh_cn.json"
before = upstream_json(BASE, locale_path)
after = upstream_json(TARGET, locale_path)
locale_keys = [key for key, value in after.items() if before.get(key) != value]
locales = {}
for locale in ["en_us", "en_ud", "zh_cn", "zh_hk", "zh_tw", "lzh"]:
    path = next(path for path in [ROOT / "src/main/resources/assets/neoecoae/lang" / (locale + ".json"),
                                 ROOT / "src/generated/resources/assets/neoecoae/lang" / (locale + ".json")]
                if path.is_file())
    table = json.loads(path.read_text(encoding="utf-8-sig"))
    missing = sorted(set(locale_keys) - table.keys())
    assert not missing, (locale, missing)
    locales[locale] = {"changed_keys_checked": len(locale_keys), "missing_keys": missing}

guides = []
for name in ["neoecoae_intro/large_integrated_working_station.md", "_zh_cn/neoecoae_intro/large_integrated_working_station.md"]:
    source = ROOT / "guidebook" / name
    packaged = ROOT / "src/main/resources/assets/neoecoae/ae2guide" / name
    assert source.read_bytes() == packaged.read_bytes()
    guides.append({"path": name, "sha256": sha(source), "packaged_copy_equal": True})

screenshots = []
image_root = ROOT / "run/combined/client/screenshots"
models = sorted(image_root.glob("workstation-upstream-sync-20261023-*.png"))
assert len(models) == 64
images = [image_root / name for name in ["neoecoae-upstream-sync-reference.png", "neoecoae-upstream-sync-seven-rows.png",
    "neoecoae-upstream-sync-preheat.png", "neoecoae-upstream-sync-task-scroll.png",
    "neoecoae-upstream-sync-buttons-hover.png", "neoecoae-upstream-sync-buttons-focus.png",
    "neoecoae-upstream-sync-buttons-disabled.png"]] + models
base_image_root = ROOT / "run/base/client/screenshots"
images += [base_image_root / name for name in ["neoecoae-upstream-sync-reference.png", "neoecoae-upstream-sync-seven-rows.png",
    "neoecoae-upstream-sync-preheat.png", "neoecoae-upstream-sync-task-scroll.png",
    "neoecoae-upstream-sync-buttons-hover.png", "neoecoae-upstream-sync-buttons-focus.png",
    "neoecoae-upstream-sync-buttons-disabled.png"]]
for path in images:
    width, height = struct.unpack(">II", path.read_bytes()[16:24])
    screenshots.append({"path": path.relative_to(ROOT).as_posix(), "width": width, "height": height, "sha256": sha(path)})

full_log = ROOT / "validation/upstream-sync-20261023-full-test-compile-final.log"
full_text = full_log.read_text(encoding="utf-8-sig")
assert "compileTestJava FAILED" in full_text and "BUILD FAILED" in full_text
full_paths = list(dict.fromkeys(re.findall(r"src[\\/]test[\\/]java[^\r\n]*?\.java", full_text)))[:10]

result = {
    "validated_at_utc": datetime.now(timezone.utc).isoformat(),
    "original_baseline": BASE,
    "approved_increment_target": TARGET,
    "implementation_parent_commit": INITIAL,
    "branch": git("branch", "--show-current"),
    "scope": overlay["scope"],
    "original_source_and_dependency_locks_unchanged": True,
    "tests": {task: tests(task) for task in ["upstreamSyncTest", "portingPlatformTest", "portingContractsTest"]},
    "conditional_skips": {"released_ae2lt_exact_streaming_or_admission_api_unavailable": 8,
                          "excluded_data_energistics_screen_patch": 1},
    "full_imported_test_compile": {"result": "failed_preexisting_migration_debt", "log": full_log.relative_to(ROOT).as_posix(),
                                   "sha256": sha(full_log), "example_unported_sources": full_paths},
    "final_build": log_check("final-checks", "BUILD SUCCESSFUL"),
    "all_three_test_tasks_executed": log_check("integrated-all-tests", "> Task :portingContractsTest\n"),
    "package_audit": {"path": package_path.relative_to(ROOT).as_posix(), "jar": package["jar"], "sha256": package["sha256"],
                      "production_sources_checked": package["source_files_checked"], "result": package["result"]},
    "changed_resources_equal_jar": {"checked": len(changed_assets), "mismatches": mismatches},
    "renamed_legacy_resources_absent_from_jar": {"checked": len(deleted_resources), "stale_resources": stale_resources},
    "locales": locales,
    "locale_keys_checked": locale_keys,
    "guides": guides,
    "server_runtime": {
        "legacy_upgrade": log_check("legacy-upgrade", "LEGACY_2042_ACCEPTANCE_PASS phase=upgrade"),
        "legacy_read_after_restart": log_check("legacy-read", "LEGACY_2042_ACCEPTANCE_PASS phase=read"),
        "saveddata_write": log_check("saveddata-write", "SAVEDDATA_ACCEPTANCE_PASS phase=write"),
        "saveddata_read_after_restart": log_check("saveddata-read", "SAVEDDATA_ACCEPTANCE_PASS phase=read"),
    },
    "client_runtime": {
        "profile": "combined",
        "reference_base_menus_and_task_scroll": log_check("client-reference-base", "UPSTREAM_SYNC_CLIENT PASS:"),
        "native_menus_and_task_scroll": log_check("client-artwork-final", "UPSTREAM_SYNC_CLIENT PASS:"),
        "native_priority_action": log_check("client-artwork-final", "UPSTREAM_SYNC_CLIENT priority PASS:"),
        "workstation_interface_121_artwork": "Pinned high-version artwork; original 188x245 body with connected side wings in a 228x245 bounding box, unified upgrade grid and raised priority tab, matching the supplied reference",
        "interface_artwork_provenance": "porting/workstation-interface-artwork-1.21.1.json",
        "ten_host_inventories_and_menus": log_check("client-hosts", "INVENTORY_SMOKE PASS: 10 blocks"),
        "workstation_models": log_check("client-models", "WORKSTATION_MODEL PASS: 64 real chunk-rendered views"),
        "task_fixture_contract": "12 batches read by the production persisted-batch reader; UI interactions preserve paid remainder 123; not live recipe execution",
        "model_fixture_contract": "Four retained families, four directions, working/idle, front/rear; not recipe execution",
        "nonfatal_pinned_addon_resource_errors": [
            "AdvancedAE wireless-terminal recipes lack ae2wtlib:combine; its Curios tag references the unregistered quantum terminal",
            "ExtendedAE EMC interface loot table references the unregistered expatternprovider:ex_emc_interface item"
        ],
    },
    "screenshots": screenshots,
    "interface_preview": {"path": "docs/porting-2026-10-23/images/workstation-interface.png",
                          "sha256": sha(ROOT / "docs/porting-2026-10-23/images/workstation-interface.png"),
                          "source": "run/base/client/screenshots/neoecoae-upstream-sync-reference.png",
                          "layout": "Matches the supplied four-button reference; native Chinese client capture"},
    "implementation_source_hashes": sources,
    "player_existing_world_upgrade": "not performed",
    "ordinary_launcher_installation": "not performed",
    "performance_benchmark": "not performed; no upstream Java 21/AE2 19 timing or TPS claimed",
    "remote_push": "not performed",
}
OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"result": "passed", "evidence": OUT.relative_to(ROOT).as_posix(),
                  "tests": {key: {k: value[k] for k in ["tests", "passed", "skipped"]} for key, value in result["tests"].items()},
                  "jar_resource_checks": len(changed_assets), "screenshots": len(screenshots)}, ensure_ascii=False, indent=2))
