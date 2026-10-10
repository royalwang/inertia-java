# Candidate API compatibility

The baseline is a **local candidate**, not a Maven Central release. Java packages and Maven coordinates remain unchanged. Binary/source compatibility uses japicmp 0.26.2; protocol, configuration defaults, runtime behavior and official-client qualification remain separate gates.

Build and install a complete candidate from `inertia-java/` (Java 21):

```sh
./mvnw --batch-mode --no-transfer-progress spotless:check install
python3 scripts/api-compatibility.py snapshot --output /absolute/path/new-candidate
```

The output must be new or empty. Snapshotting does not rebuild or install artifacts. It first runs the library artifact gate to reject missing classifiers or source/Javadoc drift. It captures all seven library JARs, copies their compile dependencies by digest, records source identity and hashes, and produces `candidate.json` plus public/protected `javap` signatures. Keep the directory intact outside the repository. Full dependencies permit comparison without suppressing missing classes. Build outputs must correspond to the source you are reviewing.

Acquire the pinned engineering tool once, using Maven checksum/repository handling:

```sh
./mvnw --batch-mode --no-transfer-progress org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
  -Dartifact=com.github.siom79.japicmp:japicmp:0.26.2:jar:jar-with-dependencies \
  -DoutputDirectory=/absolute/path/api-tools
```

Compare two independently saved candidates; neither needs to exist in a public repository:

```sh
python3 scripts/api-compatibility.py compare \
  --old /absolute/path/previous-candidate --new /absolute/path/new-candidate \
  --tool /absolute/path/api-tools/japicmp-0.26.2-jar-with-dependencies.jar \
  --output /absolute/path/new-api-report
```

Every saved input hash is checked before comparison. The tool version is checked against its Maven metadata and its digest is retained in the report. Each module gets a text/XML report; the command fails if any binary or source check fails. No missing-class suppression, silent baseline replacement or automatic download occurs during comparison. Review additions and configuration/behavior changes even when the command succeeds.

Exercise the actual tool with compiled fixtures:

```sh
python3 scripts/api-compatibility-test.py \
  --tool /absolute/path/api-tools/japicmp-0.26.2-jar-with-dependencies.jar
```

Fixtures cover compatible implementation/additive changes, removed public/protected methods, a source-only checked-exception change and tampered candidate rejection. The test never alters runtime build outputs. Private temp directories retain evidence.

`api-baseline.json` is the initial reviewed text signature inventory. To create a separately named future inventory, use `snapshot --signatures /path/to/new-reviewed-baseline.json`; an existing baseline is never overwritten. Text signatures assist review and do not replace japicmp or prove behavior compatibility. Review an intentional API break with migration instructions rather than updating baseline inputs to hide it.

## Aggregate verification

Set `INERTIA_API_BASELINE` to a saved previous candidate directory and `INERTIA_API_TOOL` to the pinned tool JAR when running `node scripts/verify.mjs`. Both paths should be absolute. The aggregate installs the current reactor, runs tool contracts, captures a new candidate and requires the comparison to pass before proceeding with the existing runtime/browser/deployment stages. Supplying a baseline without a tool fails immediately. Without a baseline, existing verification runs normally; the API comparison is not claimed as executed. The new stages can also be run individually with the commands above.
