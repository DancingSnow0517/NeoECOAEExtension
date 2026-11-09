"""Run serial, matched JVM forks against a preserved main-class JAR or the working build."""
import argparse, hashlib, json, os, platform, subprocess, time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--label', required=True)
parser.add_argument('--baseline-jar', type=Path)
parser.add_argument('--counter-fixture', type=Path)
parser.add_argument('--forks', type=int, default=3)
parser.add_argument('--java-home', type=Path, default=Path('C:/Program Files/Zulu/zulu-21'))
parser.add_argument('--only', default='')
parser.add_argument('--diagnostics', action='store_true')
parser.add_argument('--tagged', action='store_true')
parser.add_argument('--no-key-reuse', action='store_true')
parser.add_argument('--main-class', default='cn.dancingsnow.neoecoae.storage.StorageWorkloadBenchmark')
parser.add_argument('--warmup-batches', type=int, default=4)
parser.add_argument('--iterations-multiplier', type=int, default=1)
args = parser.parse_args()
if args.warmup_batches < 1 or args.iterations_multiplier < 1:
    parser.error('warmup batches and iteration multiplier must be positive')
root = Path(__file__).resolve().parents[2]
classpath = (root/'build/storage-performance/classpath.txt').read_text().strip()
if args.baseline_jar:
    parts = classpath.split(os.pathsep)
    parts = [str(args.baseline_jar.resolve()) if Path(part).resolve() == (root/'build/classes/java/main').resolve()
             else part for part in parts]
    classpath = os.pathsep.join(parts)
if args.counter_fixture:
    classpath = str(args.counter_fixture.resolve()) + os.pathsep + classpath
java = args.java_home/'bin/java.exe'
version = subprocess.run([str(java), '-version'], capture_output=True, text=True)
args.output.mkdir(parents=True, exist_ok=True)
metadata = {'label': args.label, 'java': version.stdout+version.stderr, 'os': platform.platform(),
            'baselineJar': str(args.baseline_jar) if args.baseline_jar else None,
            'baselineSha256': hashlib.sha256(args.baseline_jar.read_bytes()).hexdigest() if args.baseline_jar else None,
            'counterFixture': str(args.counter_fixture) if args.counter_fixture else None,
            'counterFixtureSha256': hashlib.sha256((args.counter_fixture/'appeng/api/stacks/KeyCounter.class').read_bytes()).hexdigest() if args.counter_fixture else None,
            'forks': []}
for fork in range(1, args.forks+1):
    directory = (args.output/f'{args.label}-{fork}').resolve()
    directory.mkdir(exist_ok=True)
    jvm_args = ['-Xms512m', '-Xmx512m', '-XX:+UseG1GC', '-Dfile.encoding=UTF-8',
                '-XX:+EnableDynamicAgentLoading',
                f'-XX:StartFlightRecording=settings=profile,filename={directory.as_posix()}/profile.jfr,dumponexit=true',
                f'-Dneoecoae.storagePerformance={str(args.diagnostics).lower()}',
                f'-Dneoecoae.benchmark.tagged={str(args.tagged).lower()}',
                f'-Dneoecoae.benchmark.keyReuse={str(not args.no_key_reuse).lower()}',
                f'-Dneoecoae.benchmark.warmupBatches={args.warmup_batches}',
                f'-Dneoecoae.benchmark.iterationsMultiplier={args.iterations_multiplier}',
                f'-Dneoecoae.benchmark.only={args.only}', '-cp', classpath,
                args.main_class, str(directory/'results.json')]
    argument_file = directory/'java.args'
    argument_file.write_text('\n'.join('"'+value.replace('\\','/').replace('"','\\"')+'"' for value in jvm_args), encoding='utf-8')
    started = time.time()
    with (directory/'jvm.log').open('w',encoding='utf-8') as log:
        result = subprocess.run([str(java),'@'+str(argument_file)], cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=300)
    metadata['forks'].append({'fork':fork,'exitCode':result.returncode,'seconds':time.time()-started,
                              'argumentsFile':str(argument_file),'result':str(directory/'results.json')})
    (args.output/f'{args.label}-metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    print(args.label, fork, result.returncode, round(time.time()-started,2), flush=True)
    if result.returncode != 0: raise SystemExit(result.returncode)
