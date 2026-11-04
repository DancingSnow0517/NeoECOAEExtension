"""Record build timing and cache evidence, including when Gradle fails."""
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def summarize(root=ROOT):
    evidence = root / 'build/compat-build-evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    log_path = evidence / 'build.log'
    raw = log_path.read_bytes() if log_path.exists() else b''
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8'
    log = raw.decode(encoding, errors='replace').replace('\r\n', '\n')
    log = re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', log)
    outcomes = re.findall(r'^> Task \S+(?: (FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED|FAILED))?$',
                          log, flags=re.MULTILINE)
    timings = [line.strip() for line in log.splitlines()
               if re.search(r'BUILD (?:SUCCESSFUL|FAILED) in |Total runtime:|'
                            r'(?:Completed|Used cache of) (?:decompile|recompile)\b', line)]
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    cold = os.environ.get('COLD_CACHE', 'false') == 'true'
    data = {
        'sourceSha': commit,
        'result': os.environ.get('CI_BUILD_RESULT', 'unknown'),
        'coldCache': cold,
        'compatProbe': os.environ.get('BUILD_COMPAT_PROBE', 'false') == 'true',
        'gradleCacheRestored': os.environ.get('GRADLE_BUILD_ACTION_CACHE_RESTORED', 'unknown'),
        'pinnedCacheHit': os.environ.get('PINNED_CACHE_HIT') or 'not restored',
        'gradleWallSeconds': int(os.environ['GRADLE_BUILD_SECONDS'])
        if os.environ.get('GRADLE_BUILD_SECONDS') else None,
        'taskCounts': {status or 'EXECUTED': outcomes.count(status) for status in sorted(set(outcomes))},
        'timingEvidence': timings,
    }
    (evidence / 'build-summary.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    summary = '\n'.join([
        '### Build and cache evidence', '',
        '| Measurement | Value |', '| --- | --- |',
        f"| Source commit | `{commit}` |",
        f"| Gradle result | {data['result']} |",
        f"| Cache mode | {'cold (restore and save disabled)' if cold else 'normal'} |",
        f"| Gradle cache restored | {data['gradleCacheRestored']} |",
        f"| Pinned dependency cache hit | {data['pinnedCacheHit']} |",
        f"| Compatibility probe | {data['compatProbe']} |",
        f"| Gradle wall time (seconds) | {data['gradleWallSeconds']} |", '',
        'Task outcomes: `' + json.dumps(data['taskCounts']) + '`', '',
        '```text', *timings, '```', '',
        'Detailed task timings are attached under `build/reports/profile/`.',
        'A restored Gradle cache does not imply that every task or Minecraft stage was cached.', '',
    ])
    (evidence / 'build-summary.md').write_text(summary, encoding='utf-8')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as output:
            output.write(summary)
    print(summary)


if __name__ == '__main__':
    summarize()
