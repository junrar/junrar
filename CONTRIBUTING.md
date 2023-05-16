# Reporting issues

Create a new issue and provide as much information as you can:
- detailed usage on the library when the problem arise
- detailed stack trace and logs
- provide the rar file if possible

# Contributing

## Requirements

You will need:

- Java JDK version 8
- Nodejs version 10+, with `npm` version 6+

## Commit messages

The commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) standard. This enables automatic versioning, releases, and release notes generation.

Commit messages are enforced using commit hooks ran on the developer's PC. To install the necessary tooling, you need to run `npm install` in the root folder of the project. This will install the necessary commit hooks.

## Build process with Gradle

The project uses Gradle for the build. You do not need to install Gradle, the Gradle wrapper will install the required version for you.

To run the tests, just run `./gradlew check`.

Before committing, run `./gradlew build` to ensure the build and tests run correctly.

## Regression testing
### Context
Regression tests are run against a corpus of RAR files. The corpus is a ~7 GB zip file containing over 13,000 RAR files.

Regression testing analyzes each RAR file in the corpus, and compares the resulting `Archive` to a serialized JSON version stored in the repo.

### Run on Github

The regression tests can be ran:
- on a PR, by commenting `/regression`
- on a branch, by manually triggering the _Regression Test_ workflow 

### Run locally

Here are the steps to run the regression tests locally:
- download the [corpus](https://drive.google.com/file/d/1BvUT1jDkXon9-uqu2-QhBHjOthWcCL8G/view?usp=sharing)
- unzip the corpus in any directory (for example `~/corpus`)
- set 2 environment variables:
  - `JUNRAR_REGRESSION_TEST_CORPUS_ROOT`: the root directory of the corpus (for example `~/corpus`)
  - `JUNRAR_REGRESSION_TEST_CORPUS_DIR`: either the root directory of the corpus, or any of its subdirectories. If a subdirectory is set, regression tests will only run on that subdirectory (for example `~/corpus/commoncrawl3`)
- run `./gradlew regressionTest`

### Update the reference data

If code changes affect the result of regression tests, you may need to regenerate the reference data.

To do so you will need to:
- change [this line](https://github.com/junrar/junrar/blob/95fa9ed87c77c4be469f805fa22622d7700e73b7/build.gradle#L187-L187) to `includeTags 'generate'`
- run `./gradlew regressionTest`
- commit the updated files in `src/regressionTest/resources/corpus`