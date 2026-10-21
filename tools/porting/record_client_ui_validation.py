"""Record the real managed-inventory/client UI regression and assemble its screenshots."""

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "validation/ui-rendering-20261021"
BLOCKS = ["storage_system_l4", "storage_system_l6", "storage_system_l9", "storage_interface",
          "crafting_interface", "computation_interface", "crafting_pattern_bus",
          "integrated_working_station", "computation_system_l4", "crafting_system_l4"]


def artifact(path):
    return {"path": path.relative_to(ROOT).as_posix(),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}


def test_results(name):
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for path in (ROOT / "build/test-results" / name).glob("TEST-*.xml"):
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
    assert totals["tests"] and not any(totals[key] for key in ("failures", "errors", "skipped")), totals
    return totals


def runtime(profile, filename):
    path = EVIDENCE / filename
    raw=path.read_bytes()
    text=raw.decode("utf-16") if raw.startswith((b"\xff\xfe",b"\xfe\xff")) else raw.decode("utf-8-sig",errors="replace")
    expected = {"neoecoae:" + block for block in BLOCKS}
    checks = {
        "predicted_client_placement": set(re.findall(r"INVENTORY_SMOKE predicted placement: (\S+)", text)) == expected,
        "server_placement_and_persistence": set(re.findall(r"INVENTORY_SMOKE server placed/persisted: (\S+),", text)) == expected,
        "menus_displayed": set(re.findall(r"INVENTORY_SMOKE menu displayed: (\S+)", text)) == expected,
        "snapshot_contents_and_dirty_notifications": "INVENTORY_SMOKE snapshot PASS:" in text,
        "registry_aware_persistence_delegation": "INVENTORY_SMOKE persistence bridge PASS:" in text,
        "scaled_last_filter_slot_reachable": "last filter slot reachable=true" in text,
        "all_dimensions_saved": "All dimensions are saved" in text,
        "gradle_success": "BUILD SUCCESSFUL" in text,
        "no_managed_field_crash": "No payload found for class" not in text,
        "no_fatal_error": "FATAL" not in text,
        "probe_completed": "INVENTORY_SMOKE PASS: 10 blocks" in text,
    }
    if profile == "combined":
        checks["all_four_addons_loaded"] = "CLIENT_SMOKE addons=[advanced_ae, expatternprovider, extendedae_plus, ae2lt]" in text
    assert all(checks.values()), (profile, checks)
    destination = EVIDENCE / ("after-" + profile)
    destination.mkdir(exist_ok=True)
    images = []
    for block in BLOCKS:
        name = "neoecoae-inventory-" + block + ".png"
        target = destination / name
        shutil.copy2(ROOT / "run" / profile / "client/screenshots" / name, target)
        with Image.open(target) as image:
            assert image.size == (854, 480), image.size
        images.append(artifact(target))
    return {"log": artifact(path), "checks": checks, "screenshots": images}


def montage():
    font_path = Path(os.environ.get("WINDIR", r"C:\Windows")) / "Fonts/msyh.ttc"
    font = ImageFont.truetype(str(font_path), 22)
    small = ImageFont.truetype(str(font_path), 16)
    rows = [("crafting_interface", "合成接口：侧栏图标与预览区裁剪"),
            ("storage_interface", "存储接口：文字和模式按钮"),
            ("computation_interface", "计算接口：小窗口显示和槽位定位")]
    canvas = Image.new("RGB", (1708, len(rows) * 530), (24, 27, 34))
    draw = ImageDraw.Draw(canvas)
    for row, (block, label) in enumerate(rows):
        y = row * 530
        draw.text((16, y + 8), "修复前 · " + label, font=font, fill=(244, 220, 220))
        draw.text((870, y + 8), "修复后 · " + label, font=font, fill=(205, 244, 222))
        name = "neoecoae-inventory-" + block + ".png"
        canvas.paste(Image.open(EVIDENCE / "before-base" / name).convert("RGB"), (0, y + 46))
        canvas.paste(Image.open(EVIDENCE / "after-base" / name).convert("RGB"), (854, y + 46))
    canvas.save(EVIDENCE / "before-after.png")
    for profile in ("base","combined"):
        files = sorted((EVIDENCE / ("after-"+profile)).glob("*.png"))
        gallery = Image.new("RGB", (854, 270 * ((len(files) + 1) // 2)), (24, 27, 34))
        draw = ImageDraw.Draw(gallery)
        for index, file in enumerate(files):
            x, y = index % 2 * 427, index // 2 * 270
            draw.text((x + 8, y + 5), file.stem.removeprefix("neoecoae-inventory-"), font=small, fill=(232, 237, 244))
            gallery.paste(Image.open(file).convert("RGB").resize((427, 240), Image.Resampling.NEAREST), (x, y + 27))
        gallery.save(EVIDENCE / ("after-"+profile+"-gallery.png"))


def main():
    report = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "main_baseline": "dc8344f48ee866e57e02c285fdda5e0fb5da9421",
        "environment": "Forge 47.4.28 / Minecraft 1.20.1 / Java 17 development client, 854x480 window",
        "initial_crash": artifact(ROOT / "validation/inventory-crash-20261021/crash-2026-10-21_18.05.27-client.txt"),
        "runtimes": {"base": runtime("base", "base-final.log"),
                     "combined": runtime("combined", "combined-final.log")},
        "tests": {name: test_results(name) for name in ("portingPlatformTest", "portingContractsTest")},
        "package": json.loads((EVIDENCE / "package-audit.json").read_text(encoding="utf-8")),
        "acceptance": ["Real client predicted item use, server placement, managed inventory snapshots and menu packets",
                       "Ten unformed block menus in base and combined profiles; framebuffer screenshots visually inspected",
                       "Scaled last-row pointer hit test through the real screen input route"],
        "limits": ["No full formed-machine operation, multiplayer, native mouse gestures or ordinary Forge installation acceptance",
                   "Full business test migration remains a separate unfinished gate",
                   "The unchanged six portingContractsTest results were reused from the verified Gradle cache"],
    }
    montage()
    report["comparison"] = artifact(EVIDENCE / "before-after.png")
    report["gallery"] = artifact(EVIDENCE / "after-base-gallery.png")
    report["combined_gallery"] = artifact(EVIDENCE / "after-combined-gallery.png")
    report["result"] = "success"
    (ROOT / "porting/client-crash-ui-validation.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total_tests=sum(result["tests"] for result in report["tests"].values())
    print(f"Recorded 20 real menu screenshots, {total_tests} passing scoped tests, and the current package audit.")


if __name__ == "__main__":
    main()
