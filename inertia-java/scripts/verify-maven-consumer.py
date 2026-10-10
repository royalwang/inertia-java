#!/usr/bin/env python3
"""Consume packaged artifacts outside the reactor using a private Maven repository/cache."""
import datetime
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parents[1]
configured_output = os.environ.get('INERTIA_CONSUMER_OUTPUT')
if configured_output:
    output = pathlib.Path(configured_output).resolve()
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError('INERTIA_CONSUMER_OUTPUT must be a new or empty directory')
    output.mkdir(parents=True, exist_ok=True)
else:
    output = pathlib.Path(tempfile.mkdtemp(prefix='inertia-maven-consumer-'))
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = ET.parse(root / 'pom.xml').getroot().findtext('m:version', namespaces=ns)
print('External Maven consumer workspace: ' + str(output), flush=True)
report = {'format': 1, 'success': False, 'version': version, 'output': str(output), 'phases': []}
report['source'] = {
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain=v1'], cwd=root, text=True).strip()),
    'verifierSha256': hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),
}
def run(name, args, cwd=root, expected=0):
    with (output / (name + '.log')).open('w') as log:
        child = subprocess.run(args, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, timeout=600)
    report['phases'].append({'name': name, 'exit': child.returncode, 'log': str(output / (name + '.log'))})
    assert child.returncode == expected if isinstance(expected, int) else child.returncode != 0, name + ': unexpected exit; see ' + str(output)
try:
    run('artifact-content', ['python3', str(root / 'scripts/verify-library-artifacts.py')])
    command = 'const m = await import(' + json.dumps((root / 'deploy/release.mjs').as_uri()) + '); process.stdout.write(m.packageRelease(process.argv[1]));'
    release = pathlib.Path(subprocess.check_output(['node', '--input-type=module', '-e', command, str(output / 'releases')], cwd=root, text=True))
    repository = output / 'repository'
    shutil.copytree(release / 'maven', repository)
    report['release'] = str(release)
    # Untimestamped, isolated SNAPSHOT fixture metadata, not a public publishing scheme.
    updated = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
    for artifact in (repository / 'io/inertia').iterdir():
        extensions = [('pom', '')] if artifact.name == 'inertia-java' else [('pom', ''), ('jar', ''), ('jar', 'sources'), ('jar', 'javadoc')]
        metadata = ET.Element('metadata')
        for name, value in [('groupId', 'io.inertia'), ('artifactId', artifact.name), ('version', version)]: ET.SubElement(metadata, name).text = value
        v = ET.SubElement(metadata, 'versioning'); ET.SubElement(v, 'lastUpdated').text = updated
        snapshots = ET.SubElement(v, 'snapshotVersions')
        for extension, classifier in extensions:
            entry = ET.SubElement(snapshots, 'snapshotVersion')
            if classifier: ET.SubElement(entry, 'classifier').text = classifier
            ET.SubElement(entry, 'extension').text = extension
            ET.SubElement(entry, 'value').text = version
            ET.SubElement(entry, 'updated').text = updated
        ET.ElementTree(metadata).write(artifact / version / 'maven-metadata.xml', encoding='utf-8', xml_declaration=True)
    for file in list(repository.rglob('*')):
        if file.is_file():
            for algorithm in ['sha1', 'sha256']:
                file.with_name(file.name + '.' + algorithm).write_text(hashlib.new(algorithm, file.read_bytes()).hexdigest() + '\n')
    consumer = output / 'consumer'; consumer.mkdir()
    (consumer / 'settings.xml').write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n')
    (consumer / 'pom.xml').write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
<modelVersion>4.0.0</modelVersion><groupId>consumer.fixture</groupId><artifactId>external-consumer</artifactId><version>1</version>
<properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
<dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.5.7</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
<repositories><repository><id>isolated-inertia-fixture</id><url>{repository.as_uri()}</url><releases><checksumPolicy>fail</checksumPolicy></releases><snapshots><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></snapshots></repository></repositories>
<dependencies><dependency><groupId>io.inertia</groupId><artifactId>inertia-session-redis</artifactId><version>{version}</version></dependency><dependency><groupId>io.inertia</groupId><artifactId>inertia-spring-boot-starter</artifactId><version>{version}</version></dependency><dependency><groupId>io.inertia</groupId><artifactId>inertia-testing</artifactId><version>{version}</version></dependency></dependencies>
<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version></plugin></plugins></build>
</project>\n''')
    source = consumer / 'src/main/java/consumer/Consumer.java'; source.parent.mkdir(parents=True)
    source.write_text('''package consumer;
import io.inertia.core.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@SpringBootConfiguration @EnableAutoConfiguration @Import(Consumer.Pages.class)
public class Consumer {
  @Bean InertiaConfig inertia() { return InertiaConfig.basic("fixture-v1", Set.of("Home")); }
  @Controller public static class Pages {
    @GetMapping("/page") public InertiaResponse page() { return new InertiaResponse("Home", Props.builder().put("message", "consumer").build()); }
    @GetMapping("/required") public InertiaResponse required() { return new InertiaResponse("Home", Props.empty()).requireSsr(); }
    static class Failure extends RuntimeException {}
    @GetMapping("/advice") public InertiaResponse fail() { throw new Failure(); }
    @ExceptionHandler(Failure.class) public InertiaResponse advice(InertiaContext context) { context.share("owner", "application"); return new InertiaResponse("Home", Props.empty()).status(418); }
    @GetMapping("/seed") public HttpOutcome seed(InertiaContext context) { context.flash("toast", "external"); return ProtocolPolicy.redirect("/page"); }
  }
  public static void main(String[] args) throws Exception {
    try (var app = (ServletWebServerApplicationContext) SpringApplication.run(Consumer.class, "--server.address=127.0.0.1", "--server.port=0")) {
      var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
      var client = HttpClient.newBuilder().cookieHandler(cookies).build();
      var base = "http://127.0.0.1:" + app.getWebServer().getPort();
      var html = client.send(HttpRequest.newBuilder(URI.create(base + "/page")).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (html.statusCode() != 200 || !html.body().contains("data-page")) throw new AssertionError("HTML adapter failed");
      var seed = client.send(HttpRequest.newBuilder(URI.create(base + "/seed")).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (seed.statusCode() != 302 || !seed.headers().firstValue("Vary").orElse("").contains("X-Inertia")) throw new AssertionError("Redirect failed");
      var advised = client.send(HttpRequest.newBuilder(URI.create(base + "/advice")).header("X-Inertia", "true").header("X-Inertia-Version", "fixture-v1").GET().build(), HttpResponse.BodyHandlers.ofString());
      var advicePage = new PageCodec().read(advised.body());
      if (advised.statusCode() != 418 || !advicePage.at("/props/owner").asText().equals("application") || advicePage.has("flash")) throw new AssertionError("Typed advice consumed original delivery or failed");
      var unavailable = client.send(HttpRequest.newBuilder(URI.create(base + "/required")).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (unavailable.statusCode() != 503 || !unavailable.headers().firstValue("Cache-Control").orElse("").contains("no-store")) throw new AssertionError("Required SSR failed");
      var codec = new PageCodec();
      for (int i=0; i<2; i++) {
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/page")).header("X-Inertia", "true").header("X-Inertia-Version", "fixture-v1").GET().build(), HttpResponse.BodyHandlers.ofString());
        var page = codec.read(response.body());
        if (response.statusCode() != 200 || !page.path("component").asText().equals("Home") || !page.at("/props/message").asText().equals("consumer")) throw new AssertionError("JSON adapter failed");
        if (i == 0 && !page.at("/flash/toast").asText().equals("external")) throw new AssertionError("Flash lost");
        if (i == 1 && page.has("flash")) throw new AssertionError("Flash replayed");
      }
      System.out.println("EXTERNAL_CONSUMER_VERIFIED HTML JSON REDIRECT REQUIRED_SSR_503 SESSION ADVICE");
    }
  }
}
''')
    # Compile the distributed guide sources unchanged, alongside this independent consumer.
    guide = consumer / 'src/main/java/io/inertia/guide'
    guide.mkdir(parents=True)
    report['guideExamples'] = []
    for name in ['CoreApiExample.java', 'SpringApiExample.java']:
        original = release / 'docs/examples' / name
        assert original.read_bytes() == (root / 'docs/examples' / name).read_bytes()
        shutil.copyfile(original, guide / name)
        report['guideExamples'].append({'file': name, 'sha256': hashlib.sha256(original.read_bytes()).hexdigest()})
    (consumer / 'src/main/java/consumer/GuideSmoke.java').write_text('''package consumer;
import io.inertia.core.PageCodec;
import io.inertia.guide.SpringApiExample;
import java.net.*;
import java.net.http.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
public class GuideSmoke {
  public static void main(String[] args) throws Exception {
    try (var app = (ServletWebServerApplicationContext) SpringApplication.run(SpringApiExample.class,
        "--server.address=127.0.0.1", "--server.port=0")) {
      var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
      var base = "http://127.0.0.1:" + app.getWebServer().getPort();
      var codec = new PageCodec();
      var html = client.send(HttpRequest.newBuilder(URI.create(base + "/guide")).build(), HttpResponse.BodyHandlers.ofString());
      if (html.statusCode() != 200 || !html.body().contains("data-page")) throw new AssertionError("Guide HTML failed");
      var partial = client.send(HttpRequest.newBuilder(URI.create(base + "/guide"))
          .header("X-Inertia", "true").header("X-Inertia-Version", "guide-v1")
          .header("X-Inertia-Partial-Component", "Home").header("X-Inertia-Partial-Data", "details")
          .build(), HttpResponse.BodyHandlers.ofString());
      var selected = codec.read(partial.body());
      if (!selected.at("/props/details/enabled").asBoolean() || selected.path("props").has("message")) throw new AssertionError("Guide partial failed");
      var stale = client.send(HttpRequest.newBuilder(URI.create(base + "/guide"))
          .header("X-Inertia", "true").header("X-Inertia-Version", "stale").build(), HttpResponse.BodyHandlers.ofString());
      if (stale.statusCode() != 409 || !stale.headers().firstValue("X-Inertia-Location").orElse("").endsWith("/guide")) throw new AssertionError("Guide stale version failed");
      for (var name : new String[] {"", "Ada"}) {
        var saved = client.send(HttpRequest.newBuilder(URI.create(base + "/guide"))
            .header("X-Inertia", "true").header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\\"name\\":\\"" + name + "\\"}")).build(), HttpResponse.BodyHandlers.ofString());
        if (saved.statusCode() != 303 || !saved.headers().firstValue("Location").orElse("").equals("/guide")) throw new AssertionError("Guide redirect failed");
        for (int visit = 0; visit < 2; visit++) {
          var response = client.send(HttpRequest.newBuilder(URI.create(base + "/guide"))
              .header("X-Inertia", "true").header("X-Inertia-Version", "guide-v1")
              .header("X-Inertia-Error-Bag", "profile").build(), HttpResponse.BodyHandlers.ofString());
          var page = codec.read(response.body());
          if (response.statusCode() != 200 || !page.path("component").asText().equals("Home") || page.path("props").has("details")) throw new AssertionError("Guide JSON failed");
          if (name.isEmpty() && visit == 0 && !page.at("/props/errors/profile/name").asText().equals("Required")) throw new AssertionError("Guide named bag failed");
          if (!name.isEmpty() && visit == 0 && !page.at("/flash/toast").asText().equals("Saved (demo only)")) throw new AssertionError("Guide flash failed");
          if (visit == 1 && (page.has("flash") || !page.at("/props/errors").isEmpty())) throw new AssertionError("Guide delivery replayed");
        }
      }
      var rest = client.send(HttpRequest.newBuilder(URI.create(base + "/guide-api"))
          .header("X-Inertia", "true").header("X-Inertia-Version", "stale").build(), HttpResponse.BodyHandlers.ofString());
      if (rest.statusCode() != 200 || rest.headers().firstValue("X-Inertia").isPresent() || !codec.read(rest.body()).path("message").asText().equals("Ordinary REST")) throw new AssertionError("Guide REST changed");
      System.out.println("GUIDE_SPRING_VERIFIED HTML PARTIAL OPTIONAL VERSION REDIRECT NAMED_BAG FLASH REST");
    }
  }
}
''')
    cache = output / 'maven-cache'
    dependency_cache = os.environ.get('INERTIA_CONSUMER_DEPENDENCY_CACHE')
    if dependency_cache:
        # Copy warm third-party/plugin dependencies, never seed the io.inertia coordinates.
        seed = pathlib.Path(dependency_cache).resolve()
        def exclude_owned(path, names):
            return ['inertia'] if pathlib.Path(path) == seed / 'io' and 'inertia' in names else []
        shutil.copytree(seed, cache, ignore=exclude_owned)
        assert not (cache / 'io/inertia').exists()
        report['thirdPartyCacheSeed'] = str(seed)
    maven = [str(root / 'mvnw'), '-B', '-ntp', '-s', str(consumer / 'settings.xml'), '-Dmaven.repo.local=' + str(cache), '-f', str(consumer / 'pom.xml')]
    run('consumer-build', maven + ['compile', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath', '-Dmdep.outputFile=' + str(consumer / 'classpath.txt')], consumer)
    classpath = (consumer / 'classpath.txt').read_text().strip()
    report['resolvedLibraries'] = []
    for module in ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-session-redis', 'inertia-spring-webmvc', 'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']:
        path = cache / 'io/inertia' / module / version / f'{module}-{version}.jar'
        assert str(path) in classpath, module + ': dependency missing from private runtime classpath'
        assert path.read_bytes() == (repository / 'io/inertia' / module / version / path.name).read_bytes()
        report['resolvedLibraries'].append({'module':module,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    assert str(root) not in classpath, 'Reactor classpath leaked into consumer'
    run('consumer-http', ['java', '-cp', str(consumer / 'target/classes') + os.pathsep + classpath, 'consumer.Consumer'], consumer)
    assert 'EXTERNAL_CONSUMER_VERIFIED' in (output / 'consumer-http.log').read_text()
    run('guide-core', ['java', '-cp', str(consumer / 'target/classes') + os.pathsep + classpath, 'io.inertia.guide.CoreApiExample'], consumer)
    guide_output = (output / 'guide-core.log').read_text().splitlines()
    assert guide_output[0] == 'redirect=303'
    guide_first, guide_next = map(json.loads, guide_output[1:])
    assert guide_first['flash']['toast'] == 'Saved' and 'flash' not in guide_next
    assert guide_first['props']['application'] == 'Guide' and 'details' not in guide_first['props']
    run('guide-spring-http', ['java', '-cp', str(consumer / 'target/classes') + os.pathsep + classpath, 'consumer.GuideSmoke'], consumer)
    assert 'GUIDE_SPRING_VERIFIED' in (output / 'guide-spring-http.log').read_text()
    # Resolve classifiers through Maven as consumers do, not by copying them into the cache.
    for classifier in ['sources', 'javadoc']:
        run('resolve-' + classifier, maven + ['org.apache.maven.plugins:maven-dependency-plugin:3.8.1:resolve', '-Dclassifier=' + classifier, '-DincludeGroupIds=io.inertia'], consumer)
        for module in report['resolvedLibraries']:
            name = f"{module['module']}-{version}-{classifier}.jar"
            path = cache / 'io/inertia' / module['module'] / version / name
            assert path.read_bytes() == (repository / 'io/inertia' / module['module'] / version / name).read_bytes()
    core = repository / 'io/inertia/inertia-core' / version / f'inertia-core-{version}.jar'
    saved = core.read_bytes()
    core.unlink()
    shutil.rmtree(cache / 'io/inertia/inertia-core')
    run('missing-core-refused', maven + ['compile'], consumer, expected='failure')
    core.write_bytes(saved)
    shutil.rmtree(cache / 'io/inertia/inertia-core', ignore_errors=True)
    run('restored-consumer', maven + ['-U', 'compile'], consumer)
    if os.environ.get('INERTIA_REDIS_SERVER'):
        cluster = output / 'redis-consumer'
        shutil.copytree(root / 'qualification/redis-cluster', cluster, ignore=shutil.ignore_patterns('target'))
        pom = cluster / 'pom.xml'
        text = pom.read_text()
        repositories = f'<repositories><repository><id>isolated-inertia-fixture</id><url>{repository.as_uri()}</url><snapshots><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></snapshots></repository></repositories>'
        pom.write_text(text.replace('<build>', repositories + '<build>'))
        run('redis-consumer-build', [str(root / 'mvnw'), '-B', '-ntp', '-s', str(consumer / 'settings.xml'), '-Dmaven.repo.local=' + str(cache), '-f', str(pom), 'package'], cluster)
        jar = cluster / 'target' / f'inertia-redis-qualification-{version}.jar'
        assert jar.is_file(), 'Independent Redis consumer executable missing'
        # The cluster process consumes this private-cache build, never the reactor fixture jar.
        os.environ['INERTIA_REDIS_CONSUMER_JAR'] = str(jar)
        os.environ['INERTIA_REDIS_CLUSTER_OUTPUT'] = str(output / 'redis-cluster')
        run('redis-cluster', ['node', str(root / 'examples/spring-react/frontend/scripts/verify-redis-cluster.mjs')])
        reports = list((output / 'redis-cluster').glob('*/summary.json'))
        assert len(reports) == 1 and json.loads(reports[0].read_text())['success']
        report['redisCluster'] = str(reports[0])
    report['success'] = True
except Exception as error:
    report['failure'] = str(error)
    raise
finally:
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    print('External Maven consumer evidence: ' + str(output / 'summary.json'), flush=True)
