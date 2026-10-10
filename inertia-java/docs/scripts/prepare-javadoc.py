"""Publish the actual Maven Javadoc classifiers; never invent an API from Java text."""
from pathlib import Path, PurePosixPath
from html.parser import HTMLParser
import hashlib
import json
import shutil
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from urllib.parse import unquote

root = Path(__file__).resolve().parents[1]
java = root.parent
version = ET.parse(java / 'pom.xml').getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
if not version or not all(c.isascii() and (c.isalnum() or c in '._-') for c in version):
    raise ValueError('Invalid Maven version')
catalog = json.loads((java.parent / 'docs/inertia-java/open-source-docs-catalog.json').read_text())
if catalog['site']['javadoc']['version'] != version:
    raise ValueError('Catalog and Maven Javadoc versions differ')
modules = ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-session-redis', 'inertia-spring-webmvc',
           'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']
destination = root / 'public/reference/javadoc' / version
manifest = {'version': version, 'modules': []}


def search_index(path, variable):
    text = path.read_text()
    start = text.index('[')
    data, end = json.JSONDecoder().raw_decode(text[start:])
    if not isinstance(data, list) or not text.startswith(variable):
        raise ValueError(f'Invalid JDK search index: {path}')
    return data


class Ids(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.ids = set()
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        self.ids.update(value for name, value in attrs if name == 'id')


# Validate all required inputs before replacing the previous generated tree.
for module in modules:
    archive = java / module / 'target' / f'{module}-{version}-javadoc.jar'
    if not archive.is_file():
        raise SystemExit(f'Missing {archive}. Run ./mvnw --batch-mode install in inertia-java first.')
destination.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='inertia-javadoc-', dir=destination.parent) as temporary:
    staging = Path(temporary)
    for module in modules:
        archive = java / module / 'target' / f'{module}-{version}-javadoc.jar'
        output = staging / module
        output.mkdir()
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                path = PurePosixPath(entry.filename)
                if path.is_absolute() or '..' in path.parts or '\\' in entry.filename or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError(f'Unsafe Javadoc archive path: {entry.filename}')
                if entry.is_dir():
                    continue
                file = output / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(source.read(entry))
        for path in ['index.html', 'resources/LICENSE', 'resources/NOTICE']:
            if not (output / path).is_file():
                raise ValueError(f'{module}: missing {path}')
        transformations = []
        stylesheet = output / 'stylesheet.css'
        optional_font = output / 'resources/fonts/dejavu.css'
        if stylesheet.is_file() and not optional_font.is_file():
            original = stylesheet.read_bytes()
            font_import = b"@import url('resources/fonts/dejavu.css');"
            if font_import in original:
                # Some actual classifiers reference unbundled optional fonts. The site's
                # existing Arial/Helvetica/serif fallbacks work without inventing font files.
                transformed = original.replace(font_import, b'/* Optional unbundled DejaVu import omitted for the site; use system fallbacks. */')
                stylesheet.write_bytes(transformed)
                transformations.append({'path': 'stylesheet.css', 'reason': 'unbundled-optional-font-import',
                                        'sourceSha256': hashlib.sha256(original).hexdigest(),
                                        'siteSha256': hashlib.sha256(transformed).hexdigest()})
        for html in output.rglob('*.html'):
            original = html.read_bytes()
            if b'rel="icon"' not in original and b'</head>' in original:
                transformed = original.replace(b'</head>', b'<link rel="icon" href="data:,"></head>', 1)
                html.write_bytes(transformed)
                transformations.append({'path': html.relative_to(output).as_posix(), 'reason': 'site-data-favicon',
                                        'sourceSha256': hashlib.sha256(original).hexdigest(),
                                        'siteSha256': hashlib.sha256(transformed).hexdigest()})
        types, members = [], []
        if module != 'inertia-spring-boot-starter':
            types = [item for item in search_index(output / 'type-search-index.js', 'typeSearchIndex') if 'p' in item]
            members = search_index(output / 'member-search-index.js', 'memberSearchIndex')
            for item in types:
                page = output / item['p'].replace('.', '/') / (item['l'] + '.html')
                if not page.is_file():
                    raise ValueError(f'Missing type page: {page}')
            for item in members:
                page = output / item['p'].replace('.', '/') / (item['c'] + '.html')
                anchor = unquote(item.get('u', item['l']))
                if anchor not in Ids(page.read_text()).ids:
                    raise ValueError(f'Missing member anchor: {module}/{item["c"]}#{anchor}')
        manifest['modules'].append({'id': module, 'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                                    'types': types, 'members': members, 'siteTransformations': transformations})
    (staging / 'api-index.json').write_text(json.dumps(manifest, indent=2) + '\n')
    shutil.rmtree(destination, ignore_errors=True)
    shutil.copytree(staging, destination)
print(f'Prepared {len(modules)} actual Javadoc classifiers; '
      f'{sum(len(m["types"]) for m in manifest["modules"])} public types; '
      f'{sum(len(m["members"]) for m in manifest["modules"])} verified member anchors')
