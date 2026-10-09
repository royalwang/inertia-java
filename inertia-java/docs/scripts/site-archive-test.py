"""Exercise archive path, inventory, and immutable-install boundaries on synthetic fixtures."""
import gzip
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import sys

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location('site_archive', Path(__file__).with_name('site-archive.py'))
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)
META = dict(format=1, version='2.3.4', tag='fixture-v2.3.4', commit='a' * 40,
            siteBase='/inertia-java/', base='/inertia-java/versions/2.3.4/')


def payload():
    data = {'index.html': b'<!doctype html><h1>Synthetic archive fixture</h1>', 'assets/site.js': b'// fixture'}
    meta = dict(META, files={name: archive.digest(value) for name, value in data.items()})
    data['snapshot.json'] = json.dumps(meta).encode()
    return data


class ArchiveContracts(unittest.TestCase):
    def test_manifest_requires_exact_content_and_source_identity(self):
        data = payload()
        archive.verify(data, META)
        for changed in [dict(data, **{'index.html': b'changed'}), dict(data, extra=b'extra'), {k: v for k, v in data.items() if k != 'assets/site.js'}]:
            with self.assertRaisesRegex(ValueError, 'inventory/content'):
                archive.verify(changed, META)
        with self.assertRaisesRegex(ValueError, 'identity mismatch'):
            archive.verify(data, dict(META, commit='b' * 40))

    def test_install_is_idempotent_and_refuses_replacement_or_parent_symlink(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            destination = root / 'versions/2.3.4'
            archive.install(payload(), destination)
            archive.install(payload(), destination)
            (destination / 'index.html').write_text('changed')
            with self.assertRaisesRegex(ValueError, 'overwrite immutable'):
                archive.install(payload(), destination)
            (root / 'linked').symlink_to(root / 'versions', target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'Symlink'):
                archive.install(payload(), root / 'linked/2.3.5')

    def test_traversal_links_and_duplicate_entries_are_rejected_before_install(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / 'unsafe.tar'
            for names in [['../escape'], ['/absolute'], ['a\\b'], ['same', 'same']]:
                with tarfile.open(file, 'w') as out:
                    for name in names:
                        member = tarfile.TarInfo(name); member.size = 1
                        out.addfile(member, io.BytesIO(b'x'))
                with self.assertRaises(ValueError):
                    archive.read_archive(file)
            with tarfile.open(file, 'w') as out:
                member = tarfile.TarInfo('link'); member.type = tarfile.SYMTYPE; member.linkname = '/outside'
                out.addfile(member)
            with self.assertRaisesRegex(ValueError, 'Link'):
                archive.read_archive(file)

    def test_entry_and_decompression_budgets_are_enforced(self):
        previous = archive.MAX_TOTAL
        archive.MAX_TOTAL = 1024
        try:
            with tempfile.TemporaryDirectory() as temp:
                file = Path(temp) / 'oversized.tar.gz'
                with tarfile.open(file, 'w:gz') as out:
                    member = tarfile.TarInfo('large'); member.size = 1025
                    out.addfile(member, io.BytesIO(b'x' * 1025))
                with self.assertRaisesRegex(ValueError, 'inventory limit'):
                    archive.read_archive(file)
                with gzip.open(file, 'wb') as out:
                    out.write(b'x' * (16 * 1024 * 1024 + 1025))
                with self.assertRaisesRegex(ValueError, 'Decompressed archive limit'):
                    archive.read_archive(file)
        finally:
            archive.MAX_TOTAL = previous

    def test_packing_identical_inputs_is_deterministic(self):
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); site = root / 'site'; site.mkdir()
            (site / 'index.html').write_bytes(payload()['index.html'])
            meta = root / 'meta.json'; meta.write_text(json.dumps(META))
            outputs = [root / 'one.tar.gz', root / 'two.tar.gz']
            for output in outputs:
                subprocess.run([sys.executable, str(Path(__file__).with_name('site-archive.py')), 'pack', '--input', str(site), '--output', str(output), '--metadata', str(meta)], check=True, capture_output=True)
            self.assertEqual(outputs[0].read_bytes(), outputs[1].read_bytes())
            archive.verify(archive.read_archive(outputs[0]), META)


if __name__ == '__main__':
    unittest.main()
