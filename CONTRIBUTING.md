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