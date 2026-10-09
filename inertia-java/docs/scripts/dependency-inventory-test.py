"""Contracts for lock completeness, repeated npm identities and observed license evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('docs_dependencies', Path(__file__).with_name('dependency-inventory.py'))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


def component(path):
    return {'bom-ref': 'fixture@1.0.0', 'name': 'fixture', 'version': '1.0.0',
            'licenses': [{'license': {'id': 'MIT'}}],
            'properties': [{'name': 'cdx:npm:package:path', 'value': path}]}


class DocumentationDependencies(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='inertia-docs-inventory-contract-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / 'evidence'
        self.output.mkdir()
        self.paths = ['node_modules/fixture', 'node_modules/parent/node_modules/fixture']
        self.bom = {'bomFormat': 'CycloneDX', 'metadata': {'component': {'bom-ref': 'owned'}},
                    'components': [component(path) for path in self.paths],
                    'dependencies': [{'ref': 'owned', 'dependsOn': ['fixture@1.0.0']}]}
        self.lock = {'packages': {'': {'name': 'owned', 'version': '0.0.0'},
                                 **{path: {'version': '1.0.0'} for path in self.paths}}}
        for path in self.paths:
            package = self.root / path
            package.mkdir(parents=True)
            (package / 'package.json').write_text(json.dumps({'name': 'fixture', 'version': '1.0.0'}))
            (package / 'LICENSE').write_bytes(b'Unchanged fixture license bytes\n')

    def collect(self):
        return collector.inventory(self.bom, self.lock, self.root, self.output)

    def test_nested_identical_components_preserve_all_paths_and_license_bytes(self):
        result = self.collect()
        self.assertEqual(result['counts']['uniqueComponents'], 1)
        self.assertEqual(result['counts']['allPlatformLockedPackagePaths'], 2)
        self.assertEqual(result['counts']['installedPackagePathsObserved'], 2)
        row = result['components'][0]
        self.assertEqual(row['lockPaths'], self.paths)
        for text in row['licenseFiles']:
            self.assertEqual((self.output / text['path']).read_bytes(), b'Unchanged fixture license bytes\n')

    def test_missing_or_duplicate_paths_and_undeclared_graph_edges_fail(self):
        original = copy.deepcopy(self.bom)
        self.bom['components'].pop()
        with self.assertRaisesRegex(ValueError, 'complete all-platform'):
            self.collect()
        self.bom = copy.deepcopy(original)
        self.bom['components'][1] = self.bom['components'][0]
        with self.assertRaisesRegex(ValueError, 'duplicate npm package path'):
            self.collect()
        self.bom = original
        self.bom['dependencies'][0]['dependsOn'] = ['missing']
        with self.assertRaisesRegex(ValueError, 'undeclared'):
            self.collect()

    def test_conflicting_declarations_or_wrong_installed_version_fail(self):
        self.bom['components'][1]['licenses'] = [{'license': {'id': 'Other'}}]
        with self.assertRaisesRegex(ValueError, 'Conflicting declarations'):
            self.collect()
        self.bom['components'][1]['licenses'] = self.bom['components'][0]['licenses']
        (self.root / self.paths[0] / 'package.json').write_text('{"name":"fixture","version":"9.9.9"}')
        with self.assertRaisesRegex(ValueError, 'differs from lock identity'):
            self.collect()

    def test_optional_absence_is_reported_and_required_absence_fails(self):
        (self.root / self.paths[0] / 'package.json').unlink()
        with self.assertRaisesRegex(ValueError, 'not installed'):
            self.collect()
        self.lock['packages'][self.paths[0]]['optional'] = True
        result = self.collect()
        self.assertEqual(result['counts']['installedPackagePathsObserved'], 1)
        self.assertTrue(any(row['reason'] == 'optional-platform-package-not-observed' for row in result['reviewItems']))

    def test_license_symlink_cannot_escape_package(self):
        package = self.root / self.paths[0]
        (package / 'LICENSE').unlink()
        target = self.root / 'outside.txt'
        target.write_text('Outside fixture text')
        (package / 'LICENSE').symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'Escaped or oversized'):
            self.collect()


if __name__ == '__main__':
    unittest.main()
