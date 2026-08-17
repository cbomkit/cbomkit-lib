# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`org.pqca:cbomkit-lib` — a Java 21 library that indexes source trees and scans them for cryptographic assets, producing a CycloneDX CBOM. Supported languages: **Java, Python, Go**. There is no `main`; consumers (e.g. CBOMkit service, cbomkit-action) drive the API directly.

## Build & test

```bash
mvn clean package            # compile + spotless apply + checkstyle + tests
mvn test                     # tests only
mvn test -Dtest=GoScannerServiceTest          # single test class
mvn test -Dtest=ScannerServiceTest#testDeduplication   # single test method
mvn spotless:apply           # format only (also runs automatically in `validate`)
```

**Dependency resolution requires GitHub Packages auth.** `com.ibm:sonar-cryptography-plugin` comes from `https://maven.pkg.github.com/cbomkit/sonar-cryptography`, so `~/.m2/settings.xml` needs a `<server><id>github</id>` entry with a GitHub username + PAT (`read:packages`). Without it the build fails at dependency resolution, not at compile.

Formatting is enforced by spotless (google-java-format, **AOSP style**, 4-space indent) bound to the `validate` phase with goal `apply` — it rewrites files rather than failing. The Apache license header in `pom.xml` is injected into every `src/**/*.java` file automatically. Checkstyle (rules inlined in `pom.xml`) runs in the same phase with goal `check` and *will* fail the build (unused imports, unused locals, `FinalClass`, `MissingOverride`, …).

## Architecture

Two phases, both abstract-base + per-language subclass:

**Indexing** (`org.pqca.indexing`) — `IndexingService.index(Path)` walks the tree and returns `List<ProjectModule>` (`identifier`, `packagePath`, `List<InputFile>`). `detectModules` recurses until `isModule(dir)` is true (build-file presence: `pom.xml`/`build.gradle*` for Java, `pyproject.toml`/`setup.cfg`/`setup.py` for Python, `go.mod` for Go); a module is never subdivided further. If no module is found anywhere, the whole base directory becomes one module. Files are wrapped as Sonar `InputFile`s via `TestInputFileBuilder`, trying UTF-8 then ISO-8859-1.

Each `*IndexService` sets default exclude regexes in its constructor (`setExcludePatterns(null)` restores them) — e.g. Java excludes `src/test/`, `package-info.java`, `module-info.java`; Go excludes `_test.go`. `GoIndexService` also indexes `go.mod` files and tags them with language id `gomod`.

**Scanning** (`org.pqca.scanning`) — `ScannerService.scan(List<ProjectModule>)` returns a `ScanResultDTO` (timings, line/file counts, `CBOM`). Detection itself lives in the external `sonar-cryptography-plugin`; this library only bridges to it:

- Each language has a `*DetectionCollectionRule` extending the plugin's `{Java,Python,Go}InventoryRule`. On each `Finding` it runs the plugin's translation process and pushes `List<INode>` into the `ScannerService` (which is a `Consumer<List<INode>>`).
- `ScannerService.accept` accumulates nodes into a `CBOMOutputFile` and, if a `IProgressDispatcher` was supplied, streams each new component as a `DETECTION` progress message.
- **Deduplication is stateful per `ScannerService` instance.** `deduplicateFindings` hashes (component name, location, line, offset) into a `Set<Integer>`; a repeated finding returns `Optional.empty()`. Do not reuse a scanner instance across independent scans.
- `getBOM()` strips the project base path from occurrence locations (`sanitizeOccurrence`, making paths relative) and resets the plugin's global `ScannerManager`.

Each language drives a different Sonar frontend: Java builds a `SensorContextTester` + `JavaFrontend` with `sonar.java.libraries`/`sonar.java.binaries`; Python parses each file itself through `PythonScannableFile` and a `PythonVisitorContext`; Go creates a `GoConverter` over a temp dir (deleted in a `finally`) and calls `CryptoGoSensor.execute`, with a `GoChecks` subclass supplying the single rule.

`CBOM` (`org.pqca.scanning.CBOM`) is a record wrapping a CycloneDX `Bom` with `merge`, `toJSON`/`formJSON` (CycloneDX 1.6), `addMetadata(gitUrl, revision, commit, subFolder)` and `write(fileName)`.

`IProgressDispatcher` is optional everywhere (null-constructor overloads exist); `send` throws `ClientDisconnected`, which propagates out of `index`/`scan`.

## Java scanning accuracy

`JavaScannerService` **throws `IllegalStateException` if the index is non-empty and no jars/class dirs were configured**, because source-only scanning is inaccurate. Configure before `scan`:

- `addJavaDependencyJar(String)` — comma-free single entry; accepts dirs or `.jar`/`.zip` glob patterns, normalized to absolute (Sonar requires absolute paths).
- `addJavaClassDir(String)` — directory glob patterns for compiled classes.
- `setRequireBuild(false)` — downgrades the exception to a warning.

## Tests

JUnit 5 + AssertJ. Tests run against fixture trees in `src/test/testdata/{java,python,go}/…` (Keycloak subset for Java, `gocrypto/*.go` for Go) plus real Bouncy Castle jars in `src/test/resources/java/scan/`. Tests index then scan then assert via `org.pqca.utils.AssertableCBOM` (`hasNumberOfDetections`, `hasDetectionWithNameAt(name, location, line)`), with **exact detection counts and line numbers** — a sonar-cryptography-plugin version bump will shift these and the expected values must be re-derived, not guessed.

Paths in assertions are relative to the repo root (occurrence locations after sanitization), so tests are working-directory sensitive.

## CI

`.github/workflows/build.yml` runs `mvn --batch-mode clean package deploy` on push/PR to `main`, publishing snapshots to GitHub Packages, then generates a CBOM of this repo via `cbomkit/cbomkit-action`. `mvn deploy` also regenerates `bom/bom.json` via the cyclonedx plugin.
