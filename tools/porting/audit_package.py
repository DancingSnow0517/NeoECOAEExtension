"""Audit an existing Forge JAR. Does not run Gradle or assert game/runtime acceptance."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]


def audit(jar: Path) -> dict:
    with ZipFile(jar) as archive:
        names = archive.namelist()
        entries = set(names)
        classes = [name for name in names if name.endswith(".class")]
        versions = Counter(int.from_bytes(archive.read(name)[6:8], "big") for name in classes)
        source_root = ROOT / "src/main/java"
        sources = sorted(p for p in source_root.rglob("*.java") if p.name not in ("package-info.java", "module-info.java"))
        missing_classes = [p.relative_to(source_root).with_suffix(".class").as_posix() for p in sources
                           if p.relative_to(source_root).with_suffix(".class").as_posix() not in entries]
        required = [
            "META-INF/mods.toml", "META-INF/MANIFEST.MF", "META-INF/accesstransformer.cfg",
            "META-INF/jarjar/metadata.json", "neoecoae.mixins.json", "neoecoae.refmap.json",
            "assets/neoecoae/lss/eco.lss", "assets/neoecoae/screens/storage/storage_interface.json",
            "assets/ae2/screens/large_integrated_working_station_interface.json",
            "assets/neoecoae/textures/gui/vendor/ae2/scrollbars.png",
        ]
        missing_resources = sorted(set(required) - entries)
        configs = sorted(name for name in names if name.endswith(".mixins.json"))
        registered = []
        plugins = set()
        for name in configs:
            config = json.loads(archive.read(name))
            prefix = config["package"].replace(".", "/") + "/"
            registered.extend(prefix + entry.replace(".", "/") + ".class"
                              for group in ("mixins", "client", "server") for entry in config.get(group, []))
            if "plugin" in config:
                plugins.add(config["plugin"].replace(".", "/") + ".class")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "").replace("\n ", "")
        manifest_configs = next(line.split(": ", 1)[1].split(",") for line in manifest.splitlines() if line.startswith("MixinConfigs: "))
        refmap = json.loads(archive.read("neoecoae.refmap.json"))
        nested = json.loads(archive.read("META-INF/jarjar/metadata.json"))
        nested_paths = [entry["path"] for entry in nested["jars"]]
        language_keys = ["gui.neoecoae.computation.pause", "gui.neoecoae.computation.resume", "item.neoecoae.structure_terminal"]
        missing_translations = []
        for locale in ("en_us", "en_ud", "zh_cn", "zh_hk", "zh_tw", "lzh"):
            path = f"assets/neoecoae/lang/{locale}.json"
            table = json.loads(archive.read(path))
            missing_translations.extend(f"{locale}:{key}" for key in language_keys if key not in table)
        fake_upstream_classes = [name for name in classes
                                 if name.startswith(("com/lowdragmc/lowdraglib2/", "dev/vfyjxf/taffy/"))]
        problems = {
            "missing_source_classes": missing_classes,
            "missing_required_resources": missing_resources,
            "missing_registered_mixins": sorted(set(registered) - entries),
            "missing_mixin_plugins": sorted(plugins - entries),
            "unregistered_mixin_configs": sorted(set(configs) - set(manifest_configs)),
            "missing_nested_jars": sorted(set(nested_paths) - entries),
            "missing_translations": missing_translations,
            "fake_upstream_classes": fake_upstream_classes,
            "duplicate_entries": sorted(name for name, count in Counter(names).items() if count > 1),
        }
        if any(version > 61 for version in versions):
            problems["unsupported_class_versions"] = [version for version in versions if version > 61]
        if not refmap.get("mappings"):
            problems["empty_refmap"] = ["mappings"]
        result = {
            "jar": jar.relative_to(ROOT).as_posix(),
            "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            "bytes": jar.stat().st_size,
            "source_files_checked": len(sources),
            "packaged_classes": len(classes),
            "class_major_versions": dict(sorted(versions.items())),
            "registered_mixins": len(registered),
            "registered_mixin_configs": configs,
            "refmap_classes": len(refmap.get("mappings", {})),
            "nested_jars": nested_paths,
            "problems": problems,
            "result": "success" if not any(problems.values()) else "failed",
            "acceptance": "static package audit only; no client/server launch, interactions or ordinary installation",
        }
        return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default="build/libs/neoecoae-20.6.0-dev.jar")
    parser.add_argument("--output", default="porting/package-validation.json")
    args = parser.parse_args()
    result = audit(ROOT / args.jar)
    (ROOT / args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["result"] == "success" else 1)
