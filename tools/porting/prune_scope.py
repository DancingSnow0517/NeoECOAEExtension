"""Remove explicitly excluded source/resource files; record hashes and unresolved boundaries.

This is a one-time migration operation, not a build-time source exclusion mechanism.
Recipe viewers, LDLib and shared preview DTOs are retained for core UI functionality.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
BASE = Path("src/main/java/cn/dancingsnow/neoecoae")
REPORT = ROOT / "porting/scope-cleanup.json"
SHARED = ("integration/emi/", "integration/jei/", "integration/xei/", "integration/ldlib/")
DROP_PACKAGES = {
    "compat.dataenergistics", "compat.mekenergistics", "compat.omnisequence", "compat.useless",
    "integration.ae2omnicells", "integration.ae2pattern", "integration.appbot", "integration.appex",
    "integration.appflux", "integration.appliedcreate", "integration.appliedpneumatics",
    "integration.appliedsoul", "integration.appmek", "integration.apprep", "integration.arsenergistique",
    "integration.biggerae2", "integration.dataenergistics", "integration.jade", "integration.kubejs",
    "integration.megacells",
}
DROP_FIXTURES = ("com/beipuo/mekenergistics/", "com/tttsaurus/omnisequence/", "com/tonywww/uselessmod/")
DROP_NAMES = ("DataWorkstation", "AppliedGeneratorsWorkstation", "MekanismRecipes", "Useless", "OmniSequence")
DROP_VARIANTS = ("ae2cs", "data_energistics", "appgen", "other")


def main() -> None:
    if REPORT.exists():
        raise SystemExit("Scope cleanup already recorded; use the manifest to review subsequent changes.")
    audit = json.loads((ROOT / "docs/porting-2026-10-20/evidence/four-addon-scope.json").read_text(encoding="utf-8"))
    removed = []

    def remove(path: Path, reason: str) -> None:
        target = path.resolve()
        if not target.is_relative_to(ROOT) or not target.is_file():
            if target.is_relative_to(ROOT) and not target.exists():
                return
            raise RuntimeError(f"Invalid cleanup target: {target}")
        removed.append({"path": target.relative_to(ROOT).as_posix(),
                        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "reason": reason})
        target.unlink()

    for relative in audit["excluded_addon_java_packages"]:
        if relative.startswith(SHARED):
            continue
        remove(ROOT / BASE / relative, "excluded addon implementation")

    for folder in (ROOT / BASE, ROOT / "src/test/java/cn/dancingsnow/neoecoae"):
        for path in list(folder.rglob("*.java")):
            relative = path.relative_to(folder).as_posix()
            package = relative.replace("/", ".")
            if any(package.startswith(prefix + ".") for prefix in DROP_PACKAGES):
                remove(path, "excluded addon implementation or focused test")
            elif path.stem.startswith(DROP_NAMES):
                remove(path, "excluded recipe or integration scenario")

    tests = ROOT / "src/test/java"
    for path in list(tests.rglob("*.java")):
        if path.relative_to(tests).as_posix().startswith(DROP_FIXTURES):
            remove(path, "excluded addon test fixture")

    keep_mixins = json.loads((ROOT / "docs/porting-2026-10-20/evidence/scoped-mixins.json").read_text(encoding="utf-8"))
    retained_files = {item["file"] for item in keep_mixins}
    for path in list((ROOT / BASE / "mixins").rglob("*.java")):
        # Helpers/plugins without @Mixin are reviewed as boundaries, not blindly deleted.
        if path.relative_to(ROOT).as_posix() not in retained_files and "@Mixin(" in path.read_text(encoding="utf-8"):
            remove(path, "Mixin outside retained core/four-addon audit")
    config_names = {item["config"] for item in keep_mixins}
    for path in list((ROOT / "src/main/resources").glob("*.mixins.json")):
        if path.name not in config_names:
            remove(path, "excluded addon Mixin config")
            continue
        config = json.loads(path.read_text(encoding="utf-8"))
        allowed = {item["class"] for item in keep_mixins if item["config"] == path.name}
        for side in ("mixins", "client", "server"):
            if side in config:
                config[side] = [name for name in config[side] if config["package"] + "." + name in allowed]
        config["compatibilityLevel"] = "JAVA_17"
        config["refmap"] = "neoecoae.refmap.json"
        path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")

    for path in list((ROOT / "src").rglob("*")):
        if not path.is_file() or path.suffix == ".java":
            continue
        relative = path.relative_to(ROOT).as_posix()
        if "integrated_working_station_module_" in path.name and any(
                "module_" + variant in path.name for variant in DROP_VARIANTS):
            remove(path, "excluded workstation upgrade-card resource/recipe")
        elif "/integrated_working_station_modules/" in relative and path.stem in DROP_VARIANTS:
            remove(path, "excluded workstation module artwork")
        elif "/large_integrated_working_station_compat/" in relative and any(
                path.stem == variant or path.stem.startswith(variant + "_") for variant in DROP_VARIANTS):
            remove(path, "excluded workstation formed-model resource")
        elif path.name == "kubejs.plugins.txt":
            remove(path, "excluded KubeJS entry point")

    boundaries = []
    pattern = re.compile(r"cn\.dancingsnow\.neoecoae\.((?:compat|integration)\.[\w.]+)")
    for path in (ROOT / BASE).rglob("*.java"):
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if any(any(m.group(1).startswith(prefix + ".") for prefix in DROP_PACKAGES)
                   for m in pattern.finditer(line)):
                boundaries.append({"file": path.relative_to(ROOT).as_posix(), "line": line_no, "text": line.strip()})
    REPORT.write_text(json.dumps({"status": "scope cleanup in progress; unresolved boundaries explicitly listed",
        "removed_files": removed, "retained_preview_infrastructure": list(SHARED),
        "pending_source_boundaries": boundaries}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Removed {len(removed)} scoped files; {len(boundaries)} source references still need adaptation.")


if __name__ == "__main__":
    main()
