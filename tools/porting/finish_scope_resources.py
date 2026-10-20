"""Finish exact workstation resource pruning; retain original atlas coordinates and audit every deletion."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2].resolve()
VARIANTS = {'ae2cs', 'data_energistics', 'appgen', 'other'}
KEEP = {'regular', 'advanced_ae', 'extendedae', 'aelt'}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    report_path = ROOT / 'porting/scope-cleanup.json'
    report = json.loads(report_path.read_text(encoding='utf-8'))
    if report.get('resource_followup_complete'):
        raise SystemExit('Resource pruning is already recorded; review the manifest before any repeat operation.')
    removed = {entry['path']: entry for entry in report['removed_files']}

    def remove(path, reason):
        target = path.resolve()
        if not target.is_relative_to(ROOT) or not target.is_file():
            raise RuntimeError(f'Invalid scoped deletion: {target}')
        relative = target.relative_to(ROOT).as_posix()
        removed[relative] = {'path': relative, 'sha256': sha(target.read_bytes()), 'reason': reason}
        target.unlink()

    textures = ROOT / 'src/main/resources/assets/neoecoae/textures/block/working_station/controller_formed'
    for path in textures.rglob('*'):
        if path.is_file() and any(path.name.startswith(variant + '.') or path.name.startswith(variant + '_')
                                  for variant in VARIANTS):
            remove(path, 'excluded workstation-specific formed artwork')
    obsolete_ui = ROOT / 'src/main/resources/assets/neoecoae/screens/multiblock_builder.json'
    if obsolete_ui.exists(): remove(obsolete_ui, 'removed machine-only auto-build window')
    duplicate = ROOT / 'src/main/resources/assets/neoecoae/models/item/structure_terminal.json'
    generated = ROOT / 'src/generated/resources/assets/neoecoae/models/item/structure_terminal.json'
    if duplicate.exists():
        if json.loads(duplicate.read_text(encoding='utf-8-sig')) != json.loads(generated.read_text(encoding='utf-8-sig')):
            raise RuntimeError('Structure terminal model inputs differ semantically')
        remove(duplicate, 'duplicate structure-terminal model; the identical generated model and datagen registration remain')

    rewrites = []
    for path in (ROOT / 'src').rglob('blockstates/integrated_working_station.json'):
        original = path.read_bytes()
        document = json.loads(original.decode('utf-8-sig'))
        variants = document.get('variants', {})
        document['variants'] = {key: value for key, value in variants.items()
                               if next((part.split('=', 1)[1] for part in key.split(',')
                                        if part.startswith('module=')), 'regular') in KEEP}
        data = (json.dumps(document, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        path.write_bytes(data)
        rewrites.append({'path': path.relative_to(ROOT).as_posix(), 'before_sha256': sha(original),
                         'after_sha256': sha(data), 'removed_variants': len(variants) - len(document['variants'])})

    langs = ROOT / 'src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java'
    text = langs.read_text(encoding='utf-8')
    lines = []
    for line in text.splitlines():
        if 'provider.add("recipe_type.neoecoae.' in line and any(
                f'.{namespace}.' in line for namespace in ('ae2cs', 'appgen', 'data_energistics', 'extendedae_plus')):
            continue
        if 'gui.neoecoae.large_integrated_working_station.auto_build' in line:
            continue
        if any(f'gui.neoecoae.workstation.{prefix}.{variant}"' in line
               for prefix in ('module', 'module_short') for variant in VARIANTS):
            continue
        lines.append(line.replace('Extended AE (including EAEP)', 'Extended AE'))
    langs.write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')

    baseline = json.loads((ROOT / 'porting/main-import.json').read_text(encoding='utf-8'))
    manual_reasons = {
        'MultiblockBuilderUI.java': 'removed machine-only auto-build UI; shared placement backend remains',
        'CellWorkbenchMenuMixin.java': 'excluded Mega Cells-only Mixin',
        'CellWorkbenchBlockEntityMixin.java': 'excluded Mega Cells-only Mixin',
        'ECOUselessMixinPlugin.java': 'excluded addon Mixin plugin',
        'ECOOmniSequenceMixinPlugin.java': 'excluded addon Mixin plugin',
    }
    for entry in baseline['files']:
        relative = entry['path']
        if not (ROOT / relative).exists() and relative not in removed:
            reason = manual_reasons.get(Path(relative).name)
            if reason is None: raise RuntimeError(f'Unexplained deletion outside scope manifest: {relative}')
            removed[relative] = {**entry, 'reason': reason}
    report['removed_files'] = sorted(removed.values(), key=lambda entry: entry['path'])
    report['resource_followup_complete'] = True
    report['resource_rewrites'] = rewrites
    report['pending_source_boundaries'] = []
    report['status'] = 'scope applied; terminal integration, EAE recipe completion, API migration and full verification remain'
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Audited {len(removed)} removed files; {sum(x["removed_variants"] for x in rewrites)} excluded blockstate variants removed.')

if __name__ == '__main__':
    main()
