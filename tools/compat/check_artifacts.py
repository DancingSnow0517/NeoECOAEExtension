"""Reject dev mappings, leaked test doubles, or a probe accidentally bundled in the release."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('mod_version='))
    results = []
    for name, mod in [(f'neoecoae-{version}.jar', 'neoecoae'), (f'neoeco-compat-tests-{version}.jar', 'neoeco_compat')]:
        path = ROOT / 'build/libs' / name
        with zipfile.ZipFile(path) as archive:
            entries = archive.namelist()
            metadata = archive.read('META-INF/mods.toml').decode()
            if f'modId="{mod}"' not in metadata:
                raise ValueError(f'Wrong mod identity: {path}')
            if any(p.startswith(('org/gtlcore/', 'com/gregtechceu/', 'org/mockito/')) for p in entries):
                raise ValueError(f'Third-party code or test doubles leaked into {path}')
            if mod == 'neoecoae':
                if any('/compatprobe/' in p for p in entries):
                    raise ValueError('Probe leaked into release JAR')
            else:
                if 'neoeco-compat-scenarios.json' not in entries:
                    raise ValueError('Missing scenario manifest')
                # These vanilla calls must be SRG-named in the production Forge JAR.
                for entry in entries:
                    if '/compatprobe/' in entry and entry.endswith('.class'):
                        probe = archive.read(entry)
                        if b'setBlockAndUpdate' in probe or b'getBlockEntity' in probe:
                            raise ValueError('Probe still contains Mojang method names; use reobfCompatTestJar')
            results.append({'file': name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'entries': len(entries)})
    destination = ROOT / 'build/compat-build-evidence'
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'artifacts.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
