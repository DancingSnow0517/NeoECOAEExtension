"""Import only Structure Terminal language keys from its approved legacy source, plus their datagen inputs."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = Path(r'E:\Minecraft Project\NeoECOAEExtension-1.20.1')
COMMIT = json.loads((ROOT / 'porting/source-lock.json').read_text(encoding='utf-8'))['structure_terminal']['commit']
REPORT = ROOT / 'porting/structure-terminal-language.json'

def relevant(key):
    return key == 'item.neoecoae.structure_terminal' or key.startswith((
        'gui.neoecoae.structure_terminal.', 'tooltip.neoecoae.structure_terminal.'))

def main():
    if REPORT.exists(): raise SystemExit('Terminal language import already recorded.')
    entries = []
    english = None
    for path in sorted((ROOT / 'src').glob('*/resources/assets/neoecoae/lang/*.json')):
        relative = path.relative_to(ROOT).as_posix()
        result = subprocess.run(['git', '-C', str(REFERENCE), 'show', f'{COMMIT}:{relative}'],
                                capture_output=True, check=False)
        if result.returncode: continue
        source = json.loads(result.stdout.decode('utf-8-sig'))
        keys = {key: value for key, value in source.items() if relevant(key)}
        if path.name == 'en_us.json': english = keys
        existing = json.loads(path.read_text(encoding='utf-8-sig'))
        added = sorted(key for key in keys if key not in existing)
        for key in added: existing[key] = keys[key]
        if added: path.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        entries.append({'path': relative, 'source_sha256': hashlib.sha256(result.stdout).hexdigest(),
                        'imported_keys': added})
    if not english: raise RuntimeError('Approved Structure Terminal English language keys not found')
    code = ['package cn.dancingsnow.neoecoae.data.lang;', '',
            'import com.tterrag.registrate.providers.RegistrateLangProvider;', '',
            '/** Only the approved legacy Structure Terminal language contract; registration owns the item name. */',
            'final class StructureTerminalLangs {', '    private StructureTerminalLangs() {}',
            '    static void accept(RegistrateLangProvider provider) {']
    for key, value in sorted(english.items()):
        if key == 'item.neoecoae.structure_terminal': continue
        code.append(f'        provider.add({json.dumps(key)}, {json.dumps(value, ensure_ascii=True)});')
    code += ['    }', '}', '']
    (ROOT / 'src/main/java/cn/dancingsnow/neoecoae/data/lang/StructureTerminalLangs.java').write_text(
        '\n'.join(code), encoding='utf-8', newline='\n')
    target = ROOT / 'src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java'
    text = target.read_text(encoding='utf-8').replace(
        'public static void accept(RegistrateLangProvider provider) {',
        'public static void accept(RegistrateLangProvider provider) {\n        StructureTerminalLangs.accept(provider);', 1)
    target.write_text(text, encoding='utf-8', newline='\n')
    REPORT.write_text(json.dumps({'commit': COMMIT, 'scope': 'structure-terminal language keys only',
                                 'files': entries}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Imported {sum(len(e["imported_keys"]) for e in entries)} terminal keys across {len(entries)} locales.')

if __name__ == '__main__':
    main()
