"""Validate the shipped Mixin classes against pinned dependency bytecode, without launching a game."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--config', default='architecture.mixins.json')
parser.add_argument('--main-jar', type=Path)
parser.add_argument('--java-home', type=Path, default=Path('C:/Program Files/Microsoft/jdk-17.0.10.7-hotspot'))
parser.add_argument('--targets', nargs='*', default=[
    'net.minecraft.world.item.Item', 'net.minecraft.world.level.material.Fluid',
    'appeng.api.stacks.AEItemKey', 'appeng.api.stacks.AEFluidKey', 'appeng.api.stacks.KeyCounter',
    'appeng.me.storage.DelegatingMEInventory', 'appeng.util.prioritylist.PrecisePriorityList',
    'appeng.me.storage.MEInventoryHandler'])
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
directory = args.output.resolve()
directory.mkdir(parents=True, exist_ok=True)
classes = directory.parent / (directory.name + '-weaver')
classes.mkdir(exist_ok=True)
classpath = (root/'build/storage-performance/classpath.txt').read_text().strip()
if args.main_jar:
    classpath = os.pathsep.join(str(args.main_jar.resolve()) if Path(part).resolve() == (root/'build/classes/java/main').resolve()
        else part for part in classpath.split(os.pathsep))

def invoke(executable, arguments, name):
    file = classes/(name+'.args')
    file.write_text('\n'.join('"'+str(a).replace('\\','/').replace('"','\\"')+'"' for a in arguments), encoding='utf-8')
    with (classes/(name+'.log')).open('w', encoding='utf-8') as log:
        result = subprocess.run([str(args.java_home/'bin'/executable), '@'+str(file)], cwd=root,
            stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((classes/(name+'.log')).read_text(encoding='utf-8', errors='replace'))
        raise SystemExit(result.returncode)

invoke('javac.exe', ['-proc:none', '-encoding', 'UTF-8', '--release', '17', '-cp', classpath,
    '-d', classes, root/'tools/storage-performance/WeaveCounter.java'], 'javac')
invoke('java.exe', ['-cp', os.pathsep.join([str(classes), str(root/'tools/storage-performance'), classpath]),
    'WeaveCounter', directory, args.config, *args.targets], 'weave')
manifest = {str(file.relative_to(directory)): hashlib.sha256(file.read_bytes()).hexdigest()
    for file in directory.rglob('*.class')}
(directory/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(json.dumps(manifest, indent=2))
