# Reporting issues

Create a new issue and provide as much information as you can:
- detailed usage on the library when the problem arise
- detailed stack trace and logs
- provide the rar file if possible

# Contributing

## Commit messages

The commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) standard. This enables automatic versioning, releases, and release notes generation.

## Build process with Gradle

The project uses Gradle for the build. You do not need to install Gradle, the Gradle wrapper will install the required version for you.

To run the tests, just run `./gradlew check`.

Before committing, run `./gradlew build` to ensure the build and tests run correctly.

### PPMd heap oracle

`./gradlew ppmHeapDumpTest` extracts `ppm/legit-maxmb63.rar` twice using fresh
`Archive` instances, streams the live allocator bytes in the historical `[1, heapEnd)`
span, and checks both raw dumps against each other and the committed
`ppm/legit-maxmb63-heap.gz` golden. The golden is compressed raw PPMd heap state, not
extracted file content; it is generated only from that committed fixture.

Regenerate the oracle only after an intentional, reviewed change to the PPMd heap layout:

```sh
./gradlew generatePpmHeapDump
./gradlew ppmHeapDumpTest
```

## Regression testing
### Context
Regression tests run against a corpus of 12,475 RAR files. Each one is opened, and the resulting `Archive` is compared to a serialized JSON reference stored in `src/regressionTest/resources/corpus`.

The corpus ships in the repository, at `src/regressionTest/corpus/corpus.zip` (16 MB). The archives in it are **payload-stripped**: every byte the parser never reads has been overwritten with zeros, so the files carry no third-party content while driving the parser down exactly the same paths. Headers, block offsets, declared sizes, stored checksums and file lengths are all intact — only bytes that never influenced a parse are gone.

Because a stripped archive's compressed data is zeros, this corpus can never back an extraction, decompression or computed-checksum test. It covers archive-open behaviour, which is what these tests assert.

### Run locally

```sh
./gradlew regressionTest
```

There is nothing to download and nothing to configure; the build unpacks the bundled corpus into `build/regressionCorpus` first.

To run against a payload-bearing corpus instead, set both environment variables and they take precedence:
- `JUNRAR_REGRESSION_TEST_CORPUS_ROOT`: the root directory of the corpus (for example `~/corpus`)
- `JUNRAR_REGRESSION_TEST_CORPUS_DIR`: the root directory, or any subdirectory to restrict the run to (for example `~/corpus/commoncrawl3`)

### Run on Github

The regression tests run as the `Regression test` job of the main CI workflow
(`.github/workflows/ci.yml`) on every push and pull request.

### Regenerate the bundled corpus

Needed when the parser starts reading regions of an archive it did not read before — the stripped corpus only preserves bytes that the parser read at the time it was generated, so a change that reaches further into a file could read zeros where the original had data.

This step needs the original payload-bearing corpus, a ~7 GB zip of over 13,000 RAR files, which is **not** in the repository:

- [original corpus on Google Drive](https://drive.google.com/file/d/1BvUT1jDkXon9-uqu2-QhBHjOthWcCL8G/view?usp=sharing)

Unzip it anywhere (for example `~/corpus`), then:

```sh
./gradlew stripCorpus -PcorpusIn=~/corpus -PcorpusOut=/tmp/stripped
```

The task refuses to finish if stripping changed any archive's parse result. Verify the output before committing it, by running `regressionTest` against both the original and the stripped corpus and confirming the two runs agree — including when they fail. A useful check is to seed a deliberate defect in the parser and confirm both corpora produce the *same* set of failing archives; a corpus that only agrees when everything passes proves nothing.

Then rezip it as `src/regressionTest/corpus/corpus.zip`.

### Update the reference data

If code changes affect the result of regression tests, you may need to regenerate the reference data.

To do so you will need to:
- change [this line](https://github.com/junrar/junrar/blob/95fa9ed87c77c4be469f805fa22622d7700e73b7/build.gradle#L187-L187) to `includeTags 'generate'`
- run `./gradlew regressionTest`
- commit the updated files in `src/regressionTest/resources/corpus`
