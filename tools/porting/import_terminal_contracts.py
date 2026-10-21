"""Restore only the approved legacy Structure Terminal presentation/dependency closure."""
from pathlib import Path
import json
import hashlib
import re
import subprocess

REFERENCE = Path('E:/Minecraft Project/NeoECOAEExtension-1.20.1')
BASELINE = 'a6b3d776d677c3093954081d40d4871592864c3b'
ROOT = Path('src/main/java/cn/dancingsnow/neoecoae')

def read_old(name):
    return subprocess.check_output(['git', '-C', str(REFERENCE), 'show',
        BASELINE + ':src/main/java/cn/dancingsnow/neoecoae/' + name], encoding='utf-8')

seeds = '''client/gui/ldlib/NELDLibClientStyle.java
client/gui/ldlib/host/NEHostTextures.java
client/multiblock/preview/MultiblockPreviewContext.java
client/multiblock/preview/MultiblockPreviewScene.java
gui/ldlib/support/NELDLibGuiRenderState.java
gui/ldlib/support/NELDLibScreenOpener.java
gui/ldlib/support/NELDLibStyle.java
gui/ldlib/support/NELDLibText.java
gui/ldlib/support/NELDLibTextRender.java
gui/ldlib/widget/NEAe2TextButtonWidget.java
gui/ldlib/widget/NEAe2IconButtonWidget.java
gui/ldlib/widget/NELDLibSyncedStateWidget.java
gui/ldlib/widget/NELDLibMachineWidget.java
gui/ldlib/widget/NELDLibMultiblockSceneWidget.java
multiblock/placement/MultiBlockItemFormResolver.java
gui/ldlib/widget/NEMultiblockPatternViewer.java
gui/ldlib/widget/NEMultiblockPatternViewerFactory.java
gui/ldlib/widget/NEMultiblockPatternViewerWidget.java
multiblock/BuildPreviewState.java
multiblock/INEMultiblockBuildHost.java
multiblock/preview/MultiblockPatternPreviewService.java
multiblock/preview/MultiblockPatternSnapshot.java
multiblock/preview/PatternBlockEntry.java
multiblock/preview/PatternLayer.java
network/BoundedData.java
network/StateDelta.java
util/ItemStacks.java'''.splitlines()
subprocess.run(['git', '-C', str(REFERENCE), 'fetch', 'origin', '--prune'], check=True)
queue = list(seeds)
seen = set()
copied = []
while queue:
    name = queue.pop(0)
    if name in seen:
        continue
    seen.add(name)
    path = ROOT / name
    if path.exists():
        text = path.read_text(encoding='utf-8')
    else:
        text = read_old(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        copied.append(name)
    for imported in re.findall(r'import cn\.dancingsnow\.neoecoae\.([\w.]+);', text):
        parts = imported.split('.')
        class_index = next(i for i, part in enumerate(parts) if part[0].isupper())
        dependency = '/'.join(parts[:class_index + 1]) + '.java'
        if not (ROOT / dependency).exists():
            if not dependency.startswith(('client/multiblock/preview/', 'client/gui/ldlib/',
                    'gui/ldlib/', 'multiblock/preview/', 'multiblock/placement/MultiBlockItemFormResolver.java', 'network/', 'util/')):
                raise RuntimeError('Review terminal dependency ' + dependency)
            queue.append(dependency)

text = read_old('gui/ldlib/NELDLibUis.java')
start = text.index('    public static ModularUI createStructureTerminal')
end = text.index('    private NELDLibUis', start)
(ROOT / 'gui/ldlib/NELDLibUis.java').write_text('''package cn.dancingsnow.neoecoae.gui.ldlib;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStructureTerminalWidget;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
public final class NELDLibUis {
''' + text[start:end] + '    private NELDLibUis() {}\n}\n', encoding='utf-8')

text = read_old('gui/ldlib/support/NELDLibStateCodecs.java')
start = text.index('    public static void writeStructureTerminal')
end = text.index('    private NELDLibStateCodecs', start) if '    private NELDLibStateCodecs' in text[start:] else text.rfind('}')
imports = '\n'.join(line for line in text.splitlines() if line.startswith('import ') and not line.startswith('import cn.'))
(ROOT / 'gui/ldlib/support/NELDLibStateCodecs.java').write_text(
    'package cn.dancingsnow.neoecoae.gui.ldlib.support;\n' + imports +
    '\npublic final class NELDLibStateCodecs {\n    private static final int MAX_STRUCTURE_TERMINAL_MATERIALS=512;\n' + text[start:end] +
    '\nprivate NELDLibStateCodecs() {}\n}\n', encoding='utf-8')

manifest = Path('porting/structure-terminal-imports.json')
closure = sorted(seen | {'gui/ldlib/NELDLibUis.java', 'gui/ldlib/support/NELDLibStateCodecs.java'})
manifest.write_text(json.dumps({'commit': BASELINE, 'dependency_closure': closure,
    'trimmed': ['NELDLibUis', 'NELDLibStateCodecs'],
    'source_sha256': {name: hashlib.sha256(read_old(name).encode('utf-8')).hexdigest() for name in closure},
    'scope': 'Structure Terminal and necessary host, codec, preview, and rendering contracts only; main algorithms retained.'},
    indent=2) + '\n', encoding='utf-8')
print('Imported terminal dependencies:', len(copied))
print('\n'.join(copied))
