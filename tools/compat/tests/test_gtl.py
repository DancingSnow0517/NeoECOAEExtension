"""Controlled files and reports test the harness, not Minecraft compatibility."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

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
        self.assertLess(len(baseline), len(coexist))
        self.assertEqual(len(coexist), len(set(coexist)))

    def test_thirty_environments_require_loaded_gtl_recipes(self):
        spec = json.loads((ROOT / 'tools/compat/resources/neoeco-compat-scenarios.json').read_text())
        self.assertEqual(len(spec['cases']), 30)
        self.assertEqual(len(runner.expected_cases(spec, 'coexist')), 90)
        for environment in spec['cases']:
            with self.subTest(environment=environment['id']):
                self.assertEqual(environment['source'], 'kubejs/server_scripts/ae2.js')
                self.assertTrue(environment['recipes'])
                self.assertNotIn('patterns', environment)
                for recipe in environment['recipes']:
                    self.assertTrue(recipe['type'])
                    self.assertIn(':', recipe['id'])
                    self.assertGreater(recipe['runs'], 0)

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
