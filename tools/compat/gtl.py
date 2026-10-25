"""Resolve a GTL CurseForge release and test built JARs in isolated Forge servers.

Python 3.11+, standard library only. Never modifies a user's installation.
"""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import time
import tomllib
import traceback
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
API = 'https://www.curseforge.com/api/v1/mods'
USER_AGENT = 'Mozilla/5.0 NeoECO-Compatibility/1'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(url):
    request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT, 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.load(response)


def download(url, path):
    if urllib.parse.urlparse(url).scheme != 'https':
        raise ValueError('Only HTTPS download URLs are accepted')
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
        temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.part')
        with urllib.request.urlopen(request, timeout=120) as response, temporary.open('wb') as output:
            shutil.copyfileobj(response, output)
        temporary.replace(path)
    return digest(path)


def extract(path, destination):
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            if name.is_absolute() or '..' in name.parts or '\\' in entry.filename or ':' in entry.filename:
                raise ValueError(f'Unsafe archive entry: {entry.filename}')
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError(f'Archive symlink not accepted: {entry.filename}')
        archive.extractall(destination)


def cdn(file_id, name):
    if Path(name).name != name or '\\' in name:
        raise ValueError('Invalid CurseForge filename')
    return f'https://mediafilez.forgecdn.net/files/{file_id // 1000}/{file_id % 1000}/{urllib.parse.quote(name)}'


def select_release(rows):
    for row in rows:
        if (row.get('releaseType') == 1 and not row.get('isEarlyAccessContent')
                and {'1.20.1', 'Forge'} <= set(row.get('gameVersions', []))):
            return row
    raise ValueError('No stable Minecraft 1.20.1 Forge release in this page')


def resolve_release(project, file_id=None):
    if file_id:
        row = read_json(f'{API}/{project}/files/{file_id}')['data']
        return select_release([row])
    for page in range(100):
        data = read_json(f'{API}/{project}/files?pageIndex={page}&pageSize=50&sort=dateCreated&sortDescending=true')
        try:
            return select_release(data['data'])
        except ValueError:
            if not data['data']:
                raise ValueError('No matching stable GTL release') from None
    raise ValueError('Release lookup exceeded pagination limit')


def mod_ids(path):
    with zipfile.ZipFile(path) as archive:
        if 'META-INF/mods.toml' not in archive.namelist():
            return []
        return [mod['modId'] for mod in tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods']]


def expected_cases(spec, variant):
    routes = ['plain'] if variant == 'baseline' else spec['routes']
    return [f'{route}.{case["id"]}' for route in routes for case in spec['cases']]


def assess(report, expected, returncode):
    tests = report.get('tests', [])
    ids = [test.get('id') for test in tests]
    if report.get('setupError'):
        status = 'setup-error'
    elif not expected or len(ids) != len(set(ids)) or set(ids) != set(expected):
        status = 'invalid-report'
    elif any(test.get('status') != 'pass' for test in tests):
        status = 'fail'
    elif returncode != 0:
        status = 'shutdown-failure'
    else:
        status = 'pass'
    return {'status': status, 'exitCode': returncode, 'passed': sum(t.get('status') == 'pass' for t in tests),
            'expected': len(expected), 'reported': len(tests)}


def failure_hint(path):
    lines = path.read_text(errors='replace').splitlines()
    for marker in ['Mod loading has failed', 'Mixin apply', 'MixinTransformerError', 'Exception in thread',
                   'Failed to start the minecraft server', 'Encountered an unexpected exception']:
        for index, line in enumerate(lines):
            if marker in line:
                return '\n'.join(lines[max(0, index - 2):index + 18])
    return '\n'.join(lines[-20:])


def prepare_pack(profile, output, cache, file_id=None):
    release = resolve_release(profile['project'], file_id)
    archive = cache / str(release['id']) / release['fileName']
    archive_hash = download(cdn(release['id'], release['fileName']), archive)
    extracted = output / 'distribution'
    extract(archive, extracted)
    manifest = json.loads((extracted / 'manifest.json').read_text())
    if manifest['minecraft']['version'] != '1.20.1':
        raise ValueError('Unexpected Minecraft version')
    loaders = [row['id'] for row in manifest['minecraft']['modLoaders'] if row.get('primary')]
    if len(loaders) != 1 or not loaders[0].startswith('forge-'):
        raise ValueError('Pack must specify one primary Forge loader')
    forge = loaders[0].removeprefix('forge-')
    if not all(part.isdigit() for part in forge.split('.')):
        raise ValueError('Invalid Forge version')
    overrides = PurePosixPath(manifest['overrides'])
    if overrides.is_absolute() or '..' in overrides.parts:
        raise ValueError('Unsafe overrides path')
    base = output / 'pack'
    shutil.copytree(extracted / overrides, base)
    mods = base / 'mods'
    mods.mkdir(exist_ok=True)
    excluded = profile['server_exclusions']
    def obtain(row):
        info = read_json(f'{API}/{row["projectID"]}/files/{row["fileID"]}')['data']
        if info['id'] != row['fileID'] or info['projectId'] != row['projectID']:
            raise ValueError('CurseForge file identity mismatch')
        record = {'project': row['projectID'], 'fileId': row['fileID'], 'file': info['fileName']}
        if str(row['projectID']) in excluded:
            return {**record, 'excluded': excluded[str(row['projectID'])]}
        path = cache / str(info['id']) / info['fileName']
        sha = download(cdn(info['id'], info['fileName']), path)
        shutil.copy2(path, mods / path.name)
        return {**record, 'sha256': sha}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        files = list(pool.map(obtain, manifest['files']))
    identities = {}
    for jar in sorted(mods.glob('*.jar')):
        for mod in mod_ids(jar):
            if mod in identities:
                raise ValueError(f'Duplicate mod {mod}: {identities[mod]}, {jar.name}')
            identities[mod] = jar.name
    if not {'gtlcore', 'gtceu', 'ae2'} <= identities.keys():
        raise ValueError('Resolved distribution lacks GTLCore, GTM or AE2')
    record = {'project': profile['project'], 'releaseFileId': release['id'], 'releaseName': release['displayName'],
              'archiveSha256': archive_hash, 'manifestVersion': manifest.get('version'), 'forge': forge,
              'files': files, 'jars': [{'file': p.name, 'sha256': digest(p), 'mods': mod_ids(p)}
                                       for p in sorted(mods.glob('*.jar'))]}
    (output / 'pack-lock.json').write_text(json.dumps(record, indent=2) + '\n')
    return base, record


def install_forge(version, output, cache, java):
    full = f'1.20.1-{version}'
    filename = f'forge-{full}-installer.jar'
    installer = cache / filename
    sha = download(f'https://maven.minecraftforge.net/net/minecraftforge/forge/{full}/{filename}', installer)
    home = output / 'forge'
    home.mkdir()
    with (output / 'forge-install.log').open('w') as log:
        subprocess.run([java, '-jar', str(installer), '--installServer', str(home)], cwd=home,
                       stdout=log, stderr=subprocess.STDOUT, timeout=600, check=True)
    return home, sha


def run_server(variant, base, forge_home, lock, output, neoeco, probe, java, memory, timeout):
    directory = output / variant
    shutil.copytree(base, directory)
    replacements = []
    for jar in (directory / 'mods').glob('*.jar'):
        if 'neoecoae' in mod_ids(jar) or 'neoeco_compat' in mod_ids(jar):
            replacements.append({'file': jar.name, 'sha256': digest(jar)})
            jar.unlink()  # Only the newly created isolated test copy.
    if variant != 'baseline':
        shutil.copy2(neoeco, directory / 'mods' / neoeco.name)
    shutil.copy2(probe, directory / 'mods' / probe.name)
    # Distribution libraries are not authoritative: use the manifest's Forge version.
    libraries = directory / 'libraries'
    if libraries.exists():
        shutil.rmtree(libraries)
    libraries.symlink_to(forge_home / 'libraries', target_is_directory=True)
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
        'level-name=compat-world\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n'
        'spawn-protection=0\nmax-tick-time=120000\n')
    report_path = directory / 'compat-report.json'
    command = [java, f'-Xmx{memory}', f'-Dneoeco.compat.variant={variant}',
               f'-Dneoeco.compat.report={report_path}',
               f'@libraries/net/minecraftforge/forge/1.20.1-{lock["forge"]}/unix_args.txt', 'nogui']
    started = time.monotonic()
    expired = False
    with (directory / 'server-console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            expired = True
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    spec = json.loads((ROOT / 'tools/compat/resources/neoeco-compat-scenarios.json').read_text())
    expected = expected_cases(spec, variant)
    if report_path.exists():
        data = json.loads(report_path.read_text())
        result = assess(data, expected, process.returncode)
    else:
        result = {'status': 'timeout' if expired else 'no-report', 'exitCode': process.returncode,
                  'expected': len(expected), 'passed': 0, 'reported': 0,
                  'notRun': expected, 'detail': failure_hint(directory / 'server-console.log')}
    result.update(variant=variant, seconds=round(time.monotonic() - started), replacements=replacements)
    return result


def collect_evidence(output):
    evidence = output / 'evidence'
    evidence.mkdir(exist_ok=True)
    for name in ['pack-lock.json', 'summary.json', 'summary.md', 'forge-install.log']:
        if (output / name).exists():
            shutil.copy2(output / name, evidence / name)
    for variant in ['baseline', 'coexist']:
        for pattern in ['compat-report.json', 'server-console.log', 'logs/*.log', 'crash-reports/*.txt']:
            for source in (output / variant).glob(pattern):
                target = evidence / variant / source.relative_to(output / variant)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
    # Deliberately exclude third-party JARs, world saves and exported third-party bytecode.


def observations(output):
    """Compare identical plain-route fixtures without assigning blame to a mod."""
    reports = {}
    for variant in ['baseline', 'coexist']:
        path = output / variant / 'compat-report.json'
        if path.exists():
            try:
                reports[variant] = json.loads(path.read_text())
            except json.JSONDecodeError:
                # run_server already records this as a harness error; preserve its raw evidence.
                continue
    baseline = {test['id']: test for test in reports.get('baseline', {}).get('tests', [])}
    rows = []
    for variant, data in reports.items():
        for test in data.get('tests', []):
            reference = baseline.get(test['id']) if variant == 'coexist' else None
            comparison = 'baseline observation' if variant == 'baseline' else 'ECO-specific route; no identical baseline'
            if reference:
                comparison = 'same baseline outcome' if reference['status'] == test['status'] else 'differs from baseline'
            rows.append({'variant': variant, 'id': test['id'], 'status': test['status'],
                         'planClass': test.get('planClass'), 'ecoOwned': test.get('ecoOwned'),
                         'gtlGraphOwned': test.get('gtlGraphOwned'), 'comparison': comparison,
                         'error': test.get('error')})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--neoeco', type=Path, required=True)
    parser.add_argument('--probe', type=Path, required=True)
    parser.add_argument('--file-id', type=int, help='Omit to resolve latest stable CurseForge GTL release')
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing directories are rejected')
    parser.add_argument('--cache', type=Path, default=ROOT / 'build/compat-cache')
    parser.add_argument('--java', default='java')
    parser.add_argument('--timeout', type=int, default=1500)
    parser.add_argument('--memory', default='6G')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('Test server startup requires --accept-eula')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    summary = {'schema': 1, 'results': []}
    try:
        neoeco, probe = args.neoeco.resolve(), args.probe.resolve()
        if mod_ids(neoeco) != ['neoecoae'] or mod_ids(probe) != ['neoeco_compat']:
            raise ValueError('Unexpected test artifact mod identities')
        summary['artifacts'] = {p.name: digest(p) for p in [neoeco, probe]}
        summary['source'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        summary['dirty'] = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip())
        summary['java'] = subprocess.run([args.java, '-version'], capture_output=True, text=True, check=True).stderr
        profile = json.loads((ROOT / 'tools/compat/gtl.json').read_text())
        base, lock = prepare_pack(profile, output, args.cache.resolve(), args.file_id)
        forge_home, summary['forgeInstallerSha256'] = install_forge(lock['forge'], output, args.cache.resolve(), args.java)
        for variant in ['baseline', 'coexist']:
            try:
                result = run_server(variant, base, forge_home, lock, output, neoeco, probe,
                                    args.java, args.memory, args.timeout)
            except Exception:
                result = {'variant': variant, 'status': 'harness-error', 'detail': traceback.format_exc()}
            summary['results'].append(result)
            print(json.dumps(result), flush=True)
    except Exception:
        summary['setupError'] = traceback.format_exc()
        print(summary['setupError'], flush=True)
    finally:
        summary['observations'] = observations(output)
        (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        lines = ['# GTL compatibility observations', '']
        lock_path = output / 'pack-lock.json'
        if lock_path.exists():
            inputs = json.loads(lock_path.read_text())
            lines += [f"Pack: {inputs['releaseName']} (CurseForge {inputs['releaseFileId']}); "
                      f"internal manifest {inputs['manifestVersion']}; Forge {inputs['forge']}.",
                      f"Source: `{summary.get('source', 'unavailable')}`; dirty checkout: {summary.get('dirty', 'unknown')}.", '']
        lines += ['| Environment | Result | Passed / expected | Exit |', '|---|---|---|---|']
        for result in summary['results']:
            lines.append(f"| {result['variant']} | {result['status']} | {result.get('passed', 0)} / "
                         f"{result.get('expected', '?')} | {result.get('exitCode', '?')} |")
        for result in summary['results']:
            if result.get('detail'):
                lines += ['', f"**{result['variant']}: process/setup evidence**", '', '```text', result['detail'], '```']
        if summary.get('setupError'):
            lines += ['', 'Environment preparation failed:', '```text', summary['setupError'], '```']
        if summary['observations']:
            lines += ['', '| Environment / case | Status | Plan owner | Comparison |', '|---|---|---|---|']
            for row in summary['observations']:
                owner = 'ECO' if row['ecoOwned'] else 'GTL graph' if row['gtlGraphOwned'] else row['planClass'] or 'no plan'
                lines.append(f"| {row['variant']} / {row['id']} | {row['status']} | {owner} | {row['comparison']} |")
            for row in summary['observations']:
                if row['error']:
                    lines += ['', f"**{row['variant']} / {row['id']}**", '', '```text', row['error'][:3000], '```']
        lines += ['', 'These are production-server planner compatibility observations, not GUI or full progression certification.',
                  'See each compat-report.json for route ownership, recipe vectors, material observations and failure traces.']
        (output / 'summary.md').write_text('\n'.join(lines) + '\n')
        collect_evidence(output)
    return 0 if len(summary['results']) == 2 and all(r['status'] == 'pass' for r in summary['results']) else 1


if __name__ == '__main__':
    raise SystemExit(main())
