"""Provision approved release JARs and check SHA-256 before Gradle uses them."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, help="Optional previously downloaded official release directory")
    args = parser.parse_args()
    lock = json.loads((ROOT / "porting/dependencies.lock.json").read_text(encoding="utf-8"))
    folder = ROOT / "libs/pinned"
    folder.mkdir(parents=True, exist_ok=True)
    verified = []
    for artifact in lock["artifacts"]:
        target = folder / artifact["filename"]
        if target.is_file() and digest(target.read_bytes()) == artifact["sha256"]:
            data = target.read_bytes()
        else:
            cached = args.cache / artifact["filename"] if args.cache else None
            if cached and cached.is_file() and digest(cached.read_bytes()) == artifact["sha256"]:
                data = cached.read_bytes()
            else:
                request = urllib.request.Request(artifact["url"], headers={"User-Agent": "NeoECOAE-Forge-Port/1.0"})
                with urllib.request.urlopen(request, timeout=90) as response:
                    data = response.read()
            if digest(data) != artifact["sha256"]:
                raise SystemExit(f"Checksum mismatch: {artifact['mod_id']}; refusing to use changed release bytes")
            target.write_bytes(data)
        verified.append({"mod_id": artifact["mod_id"], "path": target.relative_to(ROOT).as_posix(),
                         "bytes": len(data), "sha256": digest(data)})
        # AdvancedAE embeds its required API. Use those exact bytes for compilation;
        # never install a second copy beside AdvancedAE at runtime.
        if artifact["mod_id"] == "advanced_ae":
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                names = [name for name in archive.namelist()
                         if name.startswith("META-INF/jarjar/ae2addonlib-") and name.endswith(".jar")]
                if len(names) != 1:
                    raise SystemExit("Expected exactly one embedded AE2AddonLib")
                api = archive.read(names[0])
                (folder / "AE2AddonLib20-embedded.jar").write_bytes(api)
                verified.append({"mod_id": "ae2addonlib_compile_only", "sha256": digest(api), "bytes": len(api)})
    report = ROOT / "porting/dependencies-verified.json"
    report.write_text(json.dumps({"status": "sha256 verified", "artifacts": verified}, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {len(lock['artifacts'])} release JARs and extracted the matching compile-only AE2AddonLib.")


if __name__ == "__main__":
    main()
