"""Controlled files and reports test the harness, not Minecraft compatibility."""
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest
import zipfile
import sys
from collections import Counter

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('compat_runner', ROOT / 'tools/compat/gtl.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class HarnessTests(unittest.TestCase):
    def test_archive_traversal_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'input.zip'
            with zipfile.ZipFile(archive, 'w') as zipped:
                zipped.writestr('../outside', 'test fixture')
            with self.assertRaises(ValueError):
                runner.extract(archive, root / 'out')
            self.assertFalse((root / 'outside').exists())

    def test_latest_filters_betas_and_wrong_versions(self):
        rows = [{'id': 3, 'releaseType': 2, 'gameVersions': ['1.20.1', 'Forge']},
                {'id': 2, 'releaseType': 1, 'gameVersions': ['1.21.1', 'NeoForge']},
                {'id': 1, 'releaseType': 1, 'gameVersions': ['1.20.1', 'Forge']}]
        self.assertEqual(runner.select_release(rows)['id'], 1)

    def test_no_stable_release_is_not_a_pass(self):
        with self.assertRaises(ValueError):
            runner.select_release([])

    def test_missing_duplicate_or_unknown_results_fail(self):
        for tests in [[], [{'id': 'one', 'status': 'pass'}] * 2,
                      [{'id': 'other', 'status': 'pass'}]]:
            with self.subTest(tests=tests):
                self.assertEqual(runner.assess({'tests': tests}, ['one'], 0)['status'], 'invalid-report')

    def test_success_requires_normal_exit_and_all_cases(self):
        data = {'tests': [{'id': 'one', 'status': 'pass'}]}
        self.assertEqual(runner.assess(data, ['one'], 0)['status'], 'pass')
        self.assertEqual(runner.assess(data, ['one'], -9)['status'], 'shutdown-failure')
        data['tests'][0]['status'] = 'skip'
        self.assertEqual(runner.assess(data, ['one'], 0)['status'], 'fail')

    def test_concurrency_requires_real_overlap_and_exclusive_cold_restart(self):
        # Controlled tick intervals validate report interpretation, not game behavior.
        ordinary = [{'id': 'ae2.one', 'startedAtTick': 10, 'completedAtTick': 30},
                    {'id': 'machine.two', 'startedAtTick': 12, 'completedAtTick': 50}]
        cold = {'id': 'machine.cold_restart', 'startedAtTick': 51, 'completedAtTick': 5}
        result = runner.concurrency_evidence({'tests': ordinary + [cold]})
        self.assertEqual(result, {'ordinaryScenarios': 2, 'overlapTicks': 18, 'exclusiveRestart': True})
        cold['startedAtTick'] = 49
        self.assertFalse(runner.concurrency_evidence({'tests': ordinary + [cold]})['exclusiveRestart'])
        ordinary[1]['startedAtTick'] = 31
        self.assertEqual(runner.concurrency_evidence({'tests': ordinary})['overlapTicks'], 0)
        del ordinary[0]['completedAtTick']
        self.assertEqual(runner.concurrency_evidence({'tests': ordinary})['overlapTicks'], 0)

    def test_installation_variants_start_concurrently_and_keep_result_order(self):
        # Controlled callbacks must both start before either can finish.
        barrier = threading.Barrier(2, timeout=5)
        def execute(variant):
            barrier.wait()
            return {'variant': variant}
        self.assertEqual(runner.run_variants(execute), [{'variant': 'baseline'}, {'variant': 'coexist'}])

    def test_setup_error_cannot_hide_behind_passing_cases(self):
        data = {'setupError': 'fixture failed', 'tests': [{'id': 'one', 'status': 'pass'}]}
        self.assertEqual(runner.assess(data, ['one'], 0)['status'], 'setup-error')

    def test_mod_identity_includes_manifest_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'renamed.jar'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('META-INF/mods.toml', '[[mods]]\nmodId="neoecoae"\nversion="${file.jarVersion}"\n')
                archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nImplementation-Version: 20.5.0\n')
            self.assertEqual(runner.mod_ids(path), ['neoecoae'])

    def test_case_selection_is_explicit_and_nonempty(self):
        spec = json.loads((ROOT / 'tools/compat/resources/neoeco-compat-scenarios.json').read_text())
        baseline = runner.expected_cases(spec, 'baseline')
        coexist = runner.expected_cases(spec, 'coexist')
        self.assertTrue(baseline)
        self.assertEqual(len(baseline), 40)
        self.assertEqual(len(coexist), 70)
        self.assertTrue(set(baseline) < set(coexist))
        self.assertEqual(len(set(coexist) - set(baseline)), 30)
        self.assertEqual(len(coexist), len(set(coexist)))

    def test_thirty_environments_require_loaded_gtl_recipes(self):
        spec = json.loads((ROOT / 'tools/compat/resources/neoeco-compat-scenarios.json').read_text())
        self.assertEqual(len(spec['neoecoCases']), 30)
        self.assertEqual(Counter(case['category'] for case in spec['neoecoCases']),
                         {'storage': 6, 'crafting': 8, 'processing': 10, 'recovery': 6})
        self.assertEqual([case['number'] for case in spec['neoecoCases']], list(range(1, 31)))
        self.assertEqual(len(spec['baseCases']['ae2']), 10)
        self.assertEqual(len(spec['baseCases']['gtl']), 30)
        references = {case['id'] for case in spec['baseCases']['gtl']}
        for environment in spec['neoecoCases']:
            self.assertGreater(environment['timeoutTicks'], 0)
            if 'reference' in environment:
                self.assertIn(environment['reference'], references)
        for environment in spec['baseCases']['gtl']:
            with self.subTest(environment=environment['id']):
                self.assertEqual(environment['source'], 'kubejs/server_scripts/ae2.js')
                self.assertTrue(environment['recipes'])
                self.assertNotIn('patterns', environment)
                for recipe in environment['recipes']:
                    self.assertTrue(recipe['type'])
                    self.assertIn(':', recipe['id'])
                    self.assertGreater(recipe['runs'], 0)

    def test_combined_report_keeps_all_three_suites_and_setup_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = [('ae2-report.json', {'tests': [{'id': 'ae2.storage', 'status': 'pass'}]}),
                       ('compat-report.json', {'tests': [{'id': 'gtl.recipe', 'status': 'pass'}]}),
                       ('neoeco-report.json', {'tests': [], 'setupError': 'NeoECO fixture setup failed'})]
            for name, report in reports:
                (root / name).write_text(json.dumps(report))
            combined = runner.read_reports(root)
            self.assertEqual([test['id'] for test in combined['tests']], ['ae2.storage', 'gtl.recipe'])
            self.assertIn('NeoECO fixture setup failed', combined['setupError'])
            self.assertEqual(runner.assess(combined, ['ae2.storage', 'gtl.recipe'], 0)['status'], 'setup-error')

    def test_group_counts_distinguish_missing_neoeco_tests_from_passing_base(self):
        spec = json.loads((ROOT / 'tools/compat/resources/neoeco-compat-scenarios.json').read_text())
        tests = [{'id': case, 'status': 'pass'} for case in runner.expected_cases(spec, 'baseline')]
        groups = runner.group_results({'tests': tests}, spec, 'coexist')
        self.assertEqual((groups['base_ae2']['passed'], groups['base_gtl']['passed']), (10, 30))
        self.assertEqual(groups['neoeco']['status'], 'not-run')
        self.assertEqual(groups['neoeco']['expected'], 30)
        self.assertEqual(runner.assess({'tests': tests}, runner.expected_cases(spec, 'coexist'), 0)['status'], 'invalid-report')

    def test_cold_restart_reuses_one_directory_and_preserves_both_logs(self):
        # Two small Python child processes exercise launcher mechanics, not game behavior.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / 'compat-report.json'
            script = ('from pathlib import Path; import json; '
                      'p=Path("launch-count"); n=int(p.read_text())+1 if p.exists() else 1; '
                      'p.write_text(str(n)); '
                      'Path("compat-report.json").write_text(json.dumps({"restartRequested":n==1})); '
                      'print("controlled launcher fixture",n)')
            code, expired, restarts = runner.execute_server([sys.executable, '-c', script], root, report, 15)
            self.assertEqual((code, expired, restarts), (0, False, 1))
            self.assertEqual((root / 'launch-count').read_text(), '2')
            self.assertIn('fixture 1', (root / 'server-console.log').read_text())
            self.assertIn('fixture 2', (root / 'server-console-restart.log').read_text())

    def test_repeated_restart_request_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = ('from pathlib import Path; '
                      'Path("compat-report.json").write_text(\'{"restartRequested":true}\')')
            with self.assertRaises(ValueError):
                runner.execute_server([sys.executable, '-c', script], root, root / 'compat-report.json', 15)

    def test_neoeco_restart_control_preserves_completed_base_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / 'compat-report.json'
            base.write_text('{"complete":true,"tests":[]}')
            original = base.read_bytes()
            script = ('from pathlib import Path; import json; '
                      'p=Path("launch-count"); n=int(p.read_text())+1 if p.exists() else 1; '
                      'p.write_text(str(n)); '
                      'Path("neoeco-report.json").write_text(json.dumps({"restartRequested":n==1}))')
            self.assertEqual(runner.execute_server([sys.executable, '-c', script], root, base, 15), (0, False, 1))
            self.assertEqual(base.read_bytes(), original)

    def test_comparison_preserves_baseline_failure_and_eco_specific_routes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for variant, cases in [('baseline', [{'id': 'plain.linear', 'status': 'fail'}]),
                                   ('coexist', [{'id': 'plain.linear', 'status': 'fail'},
                                                {'id': 'eco_request.linear', 'status': 'fail'}])]:
                (root / variant).mkdir()
                (root / variant / 'compat-report.json').write_text(json.dumps({'tests': cases}))
            rows = runner.observations(root)
            self.assertEqual(rows[1]['comparison'], 'same baseline outcome')
            self.assertIn('no identical baseline', rows[2]['comparison'])

    def test_evidence_does_not_upload_mods_or_world(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ['baseline/mods/test.jar', 'baseline/compat-world/level.dat',
                         'baseline/.mixin.out/Target.class', 'baseline/logs/latest.log']:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'controlled fixture')
            runner.collect_evidence(root)
            actual = [str(p.relative_to(root / 'evidence')) for p in (root / 'evidence').rglob('*') if p.is_file()]
            self.assertEqual(actual, ['baseline/logs/latest.log'])

    def test_ae2_comparison_detects_planner_changes_even_when_both_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for variant, planner in [('baseline', 'gtl.plan'), ('coexist', 'eco.plan')]:
                (root / variant).mkdir()
                (root / variant / 'ae2-report.json').write_text(json.dumps({
                    'tests': [{'id': 'ae2.native_cpu_execution', 'status': 'pass', 'planClass': planner}]}))
            self.assertEqual(runner.observations(root)[1]['comparison'], 'differs from baseline: planClass')

    def test_malformed_report_does_not_prevent_evidence_collection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'coexist').mkdir()
            (root / 'coexist/compat-report.json').write_text('{truncated')
            (root / 'coexist/server-console.log').write_text('controlled failure fixture')
            self.assertEqual(runner.observations(root), [])
            runner.collect_evidence(root)
            self.assertTrue((root / 'evidence/coexist/server-console.log').is_file())
            self.assertEqual((root / 'evidence/coexist/compat-report.json').read_text(), '{truncated')


if __name__ == '__main__':
    unittest.main()
