"""Deterministic static-site bundles and checked extraction; never extract links or overwrite versions."""
from pathlib import Path, PurePosixPath
import argparse
import gzip
import hashlib
import io
import json
import os
import re
import tarfile
import tempfile

MAX_TOTAL = 256 * 1024 * 1024
MAX_FILES = 20000


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_name(name):
    path = PurePosixPath(name)
    if (path.is_absolute() or path.as_posix() != name or '\\' in name
            or any(part in ('', '.', '..') for part in path.parts)
            or any(ord(c) < 32 or ord(c) == 127 for c in name)):
        raise ValueError('Unsafe archive path: ' + name)
    return name


def files(root):
    result = {}
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError('Symlink in site tree')
        if path.is_dir():
            continue
        if not path.is_file():
            raise ValueError('Special file in site tree')
        name = safe_name(path.relative_to(root).as_posix())
        result[name] = path.read_bytes()
    if len(result) > MAX_FILES or sum(map(len, result.values())) > MAX_TOTAL:
        raise ValueError('Site inventory limit exceeded')
    return result


def read_archive(archive):
    result, total = {}, 0
    # Bound decompression before tarfile parses PAX headers as well as file entries.
    with archive.open('rb') as raw:
        magic = raw.read(2); raw.seek(0)
        stream = gzip.GzipFile(fileobj=raw) if magic == b'\x1f\x8b' else raw
        decoded = stream.read(MAX_TOTAL + 16 * 1024 * 1024 + 1)
        if len(decoded) > MAX_TOTAL + 16 * 1024 * 1024:
            raise ValueError('Decompressed archive limit exceeded')
    with tarfile.open(fileobj=io.BytesIO(decoded), mode='r:') as source:
        for entry in source:
            if entry.isdir():
                safe_name(entry.name.rstrip('/'))
                continue
            name = safe_name(entry.name)
            if not entry.isfile() or name in result:
                raise ValueError('Link, special, or duplicate archive entry')
            total += entry.size
            if entry.size < 0 or total > MAX_TOTAL or len(result) >= MAX_FILES:
                raise ValueError('Archive inventory limit exceeded')
            result[name] = source.extractfile(entry).read()
    return result


def identity(meta):
    if meta.get('format') != 1 or not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', meta.get('version', '')):
        raise ValueError('Invalid stable snapshot version')
    if not re.fullmatch(r'[a-f0-9]{40}', meta.get('commit', '')) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._/-]*', meta.get('tag', '')):
        raise ValueError('Invalid snapshot source identity')
    if not re.fullmatch(r'/(?:[A-Za-z0-9_-]+/)*', meta.get('siteBase', '')):
        raise ValueError('Invalid site base')
    if meta.get('base') != meta['siteBase'] + 'versions/' + meta['version'] + '/':
        raise ValueError('Snapshot base/version mismatch')


def verify(data, expected):
    if 'snapshot.json' not in data or len(data['snapshot.json']) > 4 * 1024 * 1024:
        raise ValueError('Missing or oversized snapshot manifest')
    meta = json.loads(data['snapshot.json'])
    identity(meta)
    for field in ['version', 'tag', 'commit', 'base', 'siteBase']:
        if meta[field] != expected[field]:
            raise ValueError('Snapshot identity mismatch: ' + field)
    actual = {name: digest(value) for name, value in data.items() if name != 'snapshot.json'}
    if actual != meta.get('files') or not actual.get('index.html'):
        raise ValueError('Snapshot file inventory/content mismatch')
    return meta


def install(data, destination):
    if destination.is_symlink() or destination.parent.is_symlink():
        raise ValueError('Symlink snapshot destination')
    if destination.exists():
        if destination.is_symlink() or files(destination) != data:
            raise ValueError('Refusing to overwrite immutable snapshot')
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.inertia-site-', dir=destination.parent) as temporary:
        stage = Path(temporary) / 'site'
        stage.mkdir()
        for name, value in data.items():
            target = stage / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(value)
        os.rename(stage, destination)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=['pack', 'install', 'extract-source'])
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--metadata', type=Path)
    args = parser.parse_args()
    if args.operation == 'extract-source':
        if args.output.exists():
            raise ValueError('Source destination already exists')
        data = read_archive(args.input)
        install(data, args.output)
        # Git archives carry executable bits; restore only the validated regular files' execute bit.
        with tarfile.open(args.input, 'r:*') as source:
            for entry in source:
                if entry.isfile() and entry.mode & 0o111:
                    (args.output / safe_name(entry.name)).chmod(0o755)
        return
    if args.metadata is None:
        raise ValueError('Snapshot metadata is required')
    meta = json.loads(args.metadata.read_text())
    identity(meta)
    if args.operation == 'install':
        data = read_archive(args.input)
        verify(data, meta)
        install(data, args.output)
    else:
        if args.output.exists():
            raise ValueError('Archive output already exists')
        data = files(args.input)
        if 'snapshot.json' in data or 'index.html' not in data:
            raise ValueError('Invalid site root or preexisting snapshot manifest')
        meta['files'] = {name: digest(value) for name, value in data.items()}
        data['snapshot.json'] = (json.dumps(meta, sort_keys=True, separators=(',', ':')) + '\n').encode()
        verify(data, meta)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('xb') as destination:
            with gzip.GzipFile(filename='', fileobj=destination, mode='wb', mtime=0) as compressed:
                with tarfile.open(fileobj=compressed, mode='w', format=tarfile.PAX_FORMAT) as archive:
                    for name, value in sorted(data.items()):
                        entry = tarfile.TarInfo(name)
                        entry.size = len(value)
                        entry.mode = 0o644
                        entry.mtime = 0
                        archive.addfile(entry, io.BytesIO(value))


if __name__ == '__main__':
    main()
