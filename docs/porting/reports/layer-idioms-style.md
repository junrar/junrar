# junrar — cross-cutting idioms, house style, public API, build & test conventions

Evidence base: `~/git/junrar` @ master (504 commits, HEAD 99866b74 2026-06-29). All paths relative to repo root.
Purpose: migration manual input for porting newer unrar (RAR5) into junrar.

## 1. House style

### Formatting — enforced by Checkstyle (part of `./gradlew check`/`build`)

- Tool: Gradle `checkstyle` plugin, config `checkstyle.xml`, `toolVersion '10.3.2'` (build.gradle:171-174). Runs in `check`, so every CI `./gradlew build` gates it.
- Line length max **175** (checkstyle.xml `LineLength` module, `max=175`).
- **No tabs** (`FileTabCharacter`), no trailing whitespace (`RegexpSingleline` "\s+$"), newline at EOF (`NewlineAtEndOfFile`).
- Indentation: 4 spaces, `arrayInitIndent=8` (`Indentation` module).
- Braces: `LeftCurly`/`RightCurly` (K&R same-line), `NeedBraces` with `allowSingleLineStatement=true` — `if (x) return y;` one-liners are legal and used (Archive.java:292 `if (isEncrypted()) return true;`).
- Imports: `AvoidStarImport`, `RedundantImport`, `UnusedImports`; **`org.junit.jupiter.api.Assertions` is an illegal import** (checkstyle.xml `IllegalImport` `illegalPkgs`) — AssertJ is mandatory in tests.
- `ModifierOrder`, `RedundantModifier`, whitespace modules (`WhitespaceAround`, `GenericWhitespace`, `ParenPad`, `OperatorWrap`, ...), `ArrayTypeStyle`, `UpperEll`.
- **All naming and Javadoc checkstyle modules are commented out** (checkstyle.xml `MemberName`, `MethodName`, `JavadocMethod` etc. all disabled) — naming/doc style is convention, not machine-gated.
- No spotless, no .editorconfig, no formatter config beyond checkstyle. UTF-8 forced for all compile tasks (build.gradle:150-152). `.gitattributes`: `* text=auto` only.

### ArchUnit rules — hard architecture gates in the unit suite

- `src/test/java/com/github/junrar/architecture/CodingRulesTest.java` (production classes, tests excluded):
  - `NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` — no System.out/err, no printStackTrace (line 19).
  - `NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS` — no `throw new RuntimeException/Exception` bare (line 22).
  - `NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING` (line 28); `NO_CLASSES_SHOULD_USE_JODATIME` (line 25).
- `TestCodingRulesTest.java` (test classes): `no_junit_assertions` — nothing may depend on `org.junit.jupiter.api.Assertions` (line 15). Doubles the checkstyle IllegalImport.

### Naming conventions

- Ported decode/unpack internals keep **C++-carryover names verbatim**: `unpBlockType` (unpack/Unpack.java:68), `unpOldTable` (Unpack.java:66), `ppmEscChar`, `prgStack`, `oldFilterLengths`, even PascalCase public static `DBitLengthCounts` (Unpack.java:80) and a constructor param `DataIO` (Unpack.java:83). This is tolerated legacy — checkstyle naming is off — and it deliberately preserves greppability against C++ unrar sources.
- Newer/refactored code (Archive, Junrar, volume/, io/, exception/) is idiomatic Java camelCase: `firstVolume`, `unrarCallback`, `volumeManager` (Archive.java:166-194).
- Practical rule for a port: **internal decoder state may mirror C++ names** (helps diffing against unrar), but public API and new classes use plain Java naming.

### Field visibility

- House habit: `private` fields + getters/setters — FileHeader has ~25 private fields, all accessor-exposed (rarfile/FileHeader.java:41-92, getters below).
- One grandfathered exception: `ContentDescription.path`/`.size` are **public fields** (ContentDescription.java:4-5) — old API kept for compat; do not imitate.
- Internal classes are package-private: `class LocalFolderExtractor` (LocalFolderExtractor.java:13), private static nested helpers (`ExtractorExecutorHolder`, `NullOutputStream`, `EmptyInputStream` in Archive.java:645+).

### Javadoc habits

- Sparse. Legacy files carry SVN-era placeholder blocks (`DOCUMENT ME`, `@author $LastChangedBy$` — FileHeader.java:35-40). New code documents only what's non-obvious: RARVersion enum ("Known versions of the rar file format", RARVersion.java:3-5), the executor holder's tuning options (Archive.java:628-644), UnrarCallback contract (UnrarCallback.java:11-27).
- Deprecations get real Javadoc: `@deprecated As of 7.2.0, replaced by {@link #getFileName()}` (FileHeader.java:472-474, 487-489).
- `withJavadocJar()` (build.gradle:32) means Javadoc must at least *build* — malformed HTML in doc comments breaks `build`.

### License headers

- LICENSE = the UnRAR license ("Code may not be used to develop a RAR (WinRAR) compatible archiver" — LICENSE + README.md).
- Files ported from the original 2007 codebase carry the innoSysTec/Edmund Wagner + "the unrar licence applies to all junrar source" header (Archive.java:1-18, rarfile/FileHeader.java:1-12, unpack/Unpack.java:1-7).
- **Newer junrar-original files carry NO header at all** — first line is `package`: every file in exception/ (e.g. exception/UnsupportedRarV5Exception.java:1), volume/FileVolumeManager.java:1, io/RawDataIo.java:1, rarfile/RARVersion.java:1, Junrar.java:1, ContentDescription.java:1. Checkstyle's `Header` module is commented out.
- Rule for new port files: **no license header required**; keep the legacy header only when copying/moving legacy files. Never add SVN keywords (`$HeadURL$` etc.).

## 2. Language level & compatibility

- **Production floor: Java 8** — `compileJava { options.release = 8 }` (build.gradle:36-38). Only `compileJava` is pinned; CI compiles/runs on **JDK 21 temurin** (ci.yml:38-42).
- **Test code is modern Java**: regressionTest uses `record` (regression/ArchiveRecord.java:11) and `var` (RegressionTest.java) — test and regressionTest source sets compile at the toolchain level, not release 8. So: no `var`/records/`List.of` in `src/main`, fine in tests.
- `java6` legacy branch exists (remote); README pins Java 6 users to the 4.0.0 artifact. 5.0.0 BREAKING: "minimum java version bumped from 6 to 8" (CHANGELOG.md:413+).
- **No japicmp / binary-compat tooling.** Compatibility is governed socially: conventional commits `BREAKING CHANGE` → major version via `svu` (tag pattern `v[0-9]*`, prefix '' — .svu.yml) and jreleaser changelog flags (🚨 marker, build.gradle changelog format).
- Deprecation lifecycle: `@Deprecated` + `@deprecated As of X.Y.Z` Javadoc; removed at the next major — `com.github.gotson.bestbefore` annotation processor is wired **only when version ends `.0.0`** (build.gradle:18-20), i.e. expiry of deprecations is checked at major releases; CHANGELOG 6.0.0 has "remove deprecated code" as a BREAKING entry.
- **Dependency policy: single runtime dep** — `implementation 'org.slf4j:slf4j-api:2.0.17'` is the ONLY runtime dependency (build.gradle:17). Everything else is test-scoped (logback-classic testRuntimeOnly, commons-io, mockito, assertj, archunit-junit5, junit-pioneer; regressionTest adds jackson). A port must add **zero** new runtime deps; crypto is JDK `javax.crypto` (Archive.java:60, crypt/Rijndael).

## 3. Error handling architecture

- Package `com.github.junrar.exception` — full inventory (each file ~10 lines, no Javadoc, no headers):
  - `RarException extends Exception` — **checked**, root type (exception/RarException.java:3). Ctors: `(Throwable)`, `()`, `(String)`.
  - Subclasses (all `extends RarException`, ctors `(Throwable)` + `()`, message ctor only where used): `BadRarArchiveException`, `CorruptHeaderException` (also `(String)`), `CrcErrorException`, `HeaderNotInArchiveException`, `InitDeciphererFailedException`, `MainHeaderNullException`, `NotRarArchiveException`, `UnsupportedRarEncryptedException`, `UnsupportedRarV5Exception`.
- **RarException still extends `Exception`, NOT `IOException`** — the andrebrait plan to rebase it onto IOException has not landed on master. Changing it is a documented-breaking-change event (6.0.0 BREAKING: "RarException has changed" — CHANGELOG.md:389+).
- unrar ErrHandler mapping style: C++ error codes/`ErrHandler` calls become **typed exception throws at the detection site**: RAR5 signature → `throw new UnsupportedRarV5Exception()` (Archive.java:357-359); encrypted w/o password → UnsupportedRarEncryptedException; corrupt header parse → CorruptHeaderException (FileHeader ctor `throws CorruptHeaderException`, FileHeader.java:93).
- Catch-all wrap idiom at API boundary: `extractFile` catches `Exception`, rethrows RarException as-is, wraps anything else in `new RarException(e)` (Archive.java:599-605).
- Caller misuse = **unchecked**: `IllegalArgumentException` for null/nonexistent paths (Junrar.java validateRarPath/validateDestinationPath:128-147); `IllegalStateException` for path-traversal defense (LocalFolderExtractor.java:36-41, 62-66 — canonical-path prefix check, the CVE-2018-12418-class fix).
- `IOException` rides alongside as a separate checked type: canonical signature `throws RarException, IOException` (Archive.java:166, Junrar.java:24).
- New failure mode in a port ⇒ new small `XxxException extends RarException` file in exception/, matching the anemic pattern above; never a generic `RarException("message")` when the caller could dispatch on type — tests assert with `isExactlyInstanceOf` (see §6).

## 4. Logging

- **SLF4J only** (migrated from commons-logging at 6.0.0, BREAKING — CHANGELOG.md:374+). j.u.l is ArchUnit-banned; stdout is ArchUnit-banned.
- Pattern: `private static final Logger logger = LoggerFactory.getLogger(X.class);` (Junrar.java:18, Archive.java:90, LocalFolderExtractor.java:16, FileHeader.java:43).
- Levels observed: `info` for extraction progress (`logger.info("extracting: {}", fileNameString)` Junrar.java:180); `warn` for recoverable/skipped conditions (encrypted archive listing skipped Junrar.java:91; solid skip-file errors Archive.java:614-617); `error` before rethrow at facade boundary (`"Error while creating archive"` Junrar.java:106+) and for bad system-property parses (Archive.java:684-691).
- Always parameterized `{}` messages, exception passed as final argument. Tests get output via `testRuntimeOnly logback-classic`.

## 5. Public API surface

- **Facade** `com.github.junrar.Junrar` — static methods only: `extract(String,String[,password])`, `extract(File,File[,password])`, `extract(InputStream,File[,password])`, `extract(VolumeManager,File[,password])` → `List<File>`; `getContentsDescription(File|InputStream)` → `List<ContentDescription>` (Junrar.java:20-85).
- **`Archive implements Closeable, Iterable<FileHeader>`** (Archive.java:88). Try-with-resources is the documented lifecycle (tests: `try (Archive archive = new Archive(f))` GitHub86MissingDataTest.java:131). Constructors: `(File|InputStream|VolumeManager) × (UnrarCallback) × (String password)` (Archive.java:143-194). Iteration: `nextFileHeader()` (Archive.java:260) or `getFileHeaders()` (Archive.java:250) or for-each.
- Extraction paths: `extractFile(FileHeader, OutputStream)` (Archive.java:575 — includes solid-archive skip/replay logic, random access landed 7.6.0); `getInputStream(FileHeader)` (Archive.java:725) — PipedInputStream fed by a daemon cached thread pool, tunable via system properties `junrar.extractor.buffer-size`, `junrar.extractor.use-executor`, `junrar.extractor.max-threads`, `junrar.extractor.thread-keep-alive-seconds` (Archive.java:94-101, 645-675; README "Configuration").
- Password API: constructor parameter only (`private String password` Archive.java:141); no setter. `isEncrypted()` (Archive.java:279), `isPasswordProtected()` (Archive.java:292).
- `UnrarCallback` — 2-method interface for multi-volume readiness + progress (UnrarCallback.java:9-27). `ContentDescription` — dumb public-field DTO. `VolumeManager`/`Volume`/`FileVolumeManager`/`InputStreamVolumeManager` in volume/ are public API (accepted by Junrar.extract).
- Public/internal boundary: **no annotations, no package convention beyond Java visibility** — package-private = internal (LocalFolderExtractor). Everything `public` under com.github.junrar is de-facto API; rarfile/, unpack/, io/ are public-visible but treated as SPI-ish internals (7.0.0 moved/renamed them as BREAKING "name of classes have changed", CHANGELOG.md:344-366).
- Breaking-change history for calibration: 5.0.0 (VFS carve-out, Java 8), 6.0.0 (SLF4J, RarException hierarchy, ExtractArchive removal), 7.0.0 (public entry-point cleanup, volume package move, password params). All arrived as `refactor:`/`feat:` commits with `BREAKING CHANGE` footers, surfaced by jreleaser under "## BREAKING CHANGES".

## 6. Test conventions

- Framework: **JUnit 5 Jupiter 5.9.1** (build.gradle:155-157 `useJUnitJupiter`), **AssertJ mandatory** (JUnit Assertions import banned twice — checkstyle + ArchUnit). Mockito 5 and junit-pioneer (`@DefaultTimeZone`) available; commons-io (`FileUtils`, `NullOutputStream`) for scaffolding.
- Layout: `src/test/java/com/github/junrar/**`; helpers in `TestCommons` (temp-dir factory via `Files.createTempDirectory("Junrar")`, resource-copy helpers — TestCommons.java:18-33). Setup/teardown with `@BeforeEach`/`@AfterEach` creating/deleting a temp dir (AbnormalFilesTest.java:28-36); no @TempDir habit.
- Naming: mixed generations. Newer tests are BDD snake-ish camel: `givenSolidRar4File_whenExtractingOutOfOrder_thenExtractionIsDone` (ArchiveTest.java:290), `givenEncryptedRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown` (ArchiveTest.java:419); older: `extractionFromFileHappyDay` (JunrarTest.java:34), `testTikaDocs` (ArchiveTest.java:56). `@Nested` classes group scenarios (ArchiveTest.java:172, 239, 358, 437; JunrarTest.java:106).
- **Fixture .rar files**: `src/test/resources/com/github/junrar/` (38 files), loaded via `getClass().getResource(...)`. Subfolders by concern: `abnormal/` (corrupt-header.rar, mainHeaderNull.rar, loop*.rar — fuzz/CVE-class inputs), `password/`, `solid/`, `audio/` (generated by `generate-testdata.sh` — rar CLI sweeping `-mc` audio/text compression switches), `volumes/{old-numbers,new-numbers,new-part}` (multi-volume naming schemes), `bugfixes/`. `rar5.rar` exists to pin the UnsupportedRarV5Exception behaviour.
- **Corrupt-archive regression pattern** (the CVE/fuzz-fix house pattern, AbnormalFilesTest.java): `@ParameterizedTest @MethodSource` mapping `fixture path → expected exception Class`, asserted `catchThrowable(...)` + `assertThat(thrown).isInstanceOf(RarException.class)` + `.isExactlyInstanceOf(expected)` — and each fixture is exercised through **all four public surfaces** (Junrar.extract from File and from InputStream; manual Archive loop from File and from InputStream).
- **Issue-pinned bugfix tests**: package `com.github.junrar.bugfixes`, class `GitHub{NN}{Slug}Test` with the issue URL in a comment and expected values copied from C++ UnRAR output ("Data taken from UnRAR" — GitHub86MissingDataTest.java:20-22, 70-72). This is the oracle convention: **real unrar's output is the reference**.
- Multi-volume: `VolumeExtractorTest` — parameterized over the three volume-naming fixture sets, extracts and compares content.
- Resource hygiene pinned by `ResourceReleasedTest` (extract into the same dir as the rar; deletability proves handles closed).
- Run: `./gradlew check` (tests + checkstyle); **`./gradlew build` before committing** (CONTRIBUTING.md "Build process").
- Coverage: jacoco XML report (build.gradle:176-181) → codecov upload on the ubuntu CI leg (ci.yml:63-65). No repo-side codecov.yml / no hard threshold — informational badge, not a gate.

### Regression corpus (CONTRIBUTING.md "Regression testing")

- Separate Gradle JVM test suite `regressionTest` (build.gradle testing.suites:159-183) with own deps (jackson-databind/jsr310, assertj, junit-pioneer).
- Corpus: **external ~7 GB zip, >13,000 RAR files** (Google Drive link in CONTRIBUTING.md), organized `corpus/bug_trackers/...`, `commoncrawl*`, etc. NOT in the repo; **12,475 reference JSON files ARE committed** under `src/regressionTest/resources/corpus/**`.
- Mechanism (regression/RegressionTest.java): for every corpus file, build `Archive`, serialize an `ArchiveRecord` (record: `isRarV5, isEncrypted, isPasswordProtected, isOldFormat, fileHeaders[], exception` — ArchiveRecord.java:11-19; exceptions are captured as the expected outcome via `ArchiveRecord.fromException`) and `assertThat(record).isEqualTo(referenceJson)`. `@Timeout(30)` per file, `@DefaultTimeZone("UTC")`.
- Two tag modes: `check` (wired as default `includeTags 'check'`, build.gradle:176) vs `generate` — regenerating reference data means flipping that line to `'generate'`, running `./gradlew regressionTest`, committing the JSON churn (CONTRIBUTING.md "Update the reference data").
- Local run: env `JUNRAR_REGRESSION_TEST_CORPUS_ROOT` + `JUNRAR_REGRESSION_TEST_CORPUS_DIR` (subdir = scoped run), `./gradlew regressionTest`.
- **RAR5-port implication**: corpus reference JSONs currently encode `UnsupportedRarV5Exception` as the *expected* result for RAR5 members (`isRarV5` flag exists precisely for this). Landing RAR5 support flips thousands of expectations ⇒ a mandatory, reviewed `generate`-mode regeneration is part of the change, and the regression suite is the primary acceptance gate.

## 7. CI / release

- `.github/workflows/ci.yml`: `test` job on **ubuntu + macos + windows**, JDK 21 temurin, `./gradlew build` (= compile at release 8 + checkstyle + full JUnit suite + javadoc jar); JUnit report published per-OS; jacoco + codecov (ubuntu only).
- `.github/workflows/regression.yml`: runs on every PR/push **but gated by GitHub environment `regression` = manual maintainer approval** (commits 4872a/13035, 2026-05-20); downloads corpus zip from Google Drive via service account, runs `./gradlew regressionTest` with both env vars pointed at the workspace corpus.
- Release: `version` job on master computes semver with **svu** from conventional commits (tag prefix '' — .svu.yml); `release` job publishes **-SNAPSHOT to Sonatype on every master push**, and on `workflow_dispatch perform_release=true` runs **jreleaser** full release: GitHub Release + conventional-commits-preset changelog (🚨 breaking marker, perf category, scope categorization — build.gradle jreleaser block), auto-comments/labels resolved issues (`released` label), then commits `chore(release): X.Y.Z [skip ci]` with the appended CHANGELOG.md via EndBug/add-and-commit.
- What a big PR must pass: 3-OS `./gradlew build` (checkstyle + ArchUnit + unit suite + javadoc) and a maintainer-approved regression-corpus run.
- Dependabot: weekly, gradle + github-actions ecosystems (.github/dependabot.yml).

## 8. Git/PR conventions

- **Conventional Commits mandatory** (CONTRIBUTING.md "Commit messages") — they drive svu version bumps AND the generated changelog. Observed vocabulary: `feat:`, `fix:`, `perf:`, `test:`, `ci:`, `build(deps):`, `chore(release):` (git log; e.g. e0874d21 "feat: support random access for files in solid RAR4 archives", 5270d235 "perf: replace RarCRC.checkCrc with java.util.zip.CRC32", 154e3bf7 "test: disable test on windows due to path").
- Lowercase imperative summary, no trailing period; breaking changes carry a `BREAKING CHANGE:` footer (renders under "## BREAKING CHANGES" + 🚨).
- **CHANGELOG.md is generated by jreleaser** (append mode) — never hand-edit it in a PR.
- No branch-naming convention in-repo (only dependabot/* and the java6 legacy branch exist). Maintainer: Gauthier Roebroeck (gotson). Milestones + `released` label automated by jreleaser.

## 9. Skew list — what a naive C++-faithful port would violate here

1. **No generic throws**: C++ ErrHandler/error codes must become typed `XxxException extends RarException`; bare `throw new RuntimeException/Exception` fails ArchUnit (CodingRulesTest.java:22).
2. **No stdout/stderr**: any `mprintf`/`eprintf` equivalent must become SLF4J `logger.{info,warn,error}` with `{}` params (ArchUnit ban, CodingRulesTest.java:19).
3. **Java 8 API floor in src/main** (build.gradle:36-38): no `var`, records, `List.of`, newer NIO conveniences — even though tests/CI run JDK 21.
4. **Zero new runtime dependencies** — slf4j-api is the whole runtime dep list; crypto via JDK `javax.crypto` (a RAR5 AES-256/Blake2 port must be JDK-only or hand-rolled like crypt/Rijndael).
5. **No license header on new files** (exception/*, volume/* precedent); keep the innoSysTec header only on files that genuinely descend from the 2007 code; never copy unrar C++ file headers or SVN keywords.
6. **No tabs; 4-space indent; ≤175 cols; no trailing WS; no star imports** — checkstyle fails the build, not just a linter warning.
7. **Fields private + getters** on new header/model classes (FileHeader pattern), even though C++ structs are all-public; `ContentDescription`'s public fields are grandfathered, not a template.
8. **Every change ships tests**: JUnit 5 + AssertJ only (JUnit `Assertions` import is build-failing), fixture .rar committed under `src/test/resources/com/github/junrar/...`; corrupt-input fixes follow the AbnormalFilesTest parameterized fixture→exception-class pattern across all four API surfaces; issue fixes get `bugfixes/GitHub{NN}...Test` with expected values taken from real UnRAR output.
9. **Regression corpus is the acceptance mechanism**: RAR5 support invalidates the committed reference JSONs that currently expect `UnsupportedRarV5Exception` (`ArchiveRecord.isRarV5`); the port must include a reviewed `generate`-tag regeneration and pass the maintainer-approved corpus workflow.
10. **API shape rules**: new entry points go through the `Junrar` facade + `Archive` (Closeable/Iterable) lifecycle; internals package-private; password as constructor param; tunables as `junrar.extractor.*`-style system properties, parsed defensively with logged fallback (Archive.java:677-694).
11. **Conventional commits or the release train breaks**: version bumps and changelog are computed from commit messages; an API break without a `BREAKING CHANGE` footer mis-versions the release. Deprecate-then-remove-at-major (`bestbefore` processor armed on `.0.0` versions, build.gradle:18-20).
12. **RarException is a checked `Exception`** (not IOException, not unchecked) — the C++ longjmp-style ErrHandler must be re-expressed as checked-exception flow through `throws RarException, IOException` signatures; changing the hierarchy is a major-version event.
13. C++ identifier carryover (`unpBlockType`, `DBitLengthCounts`) is **accepted in decoder internals** for diffability against unrar — do not "clean it up" wholesale, and conversely don't export C++-style names into public API.
