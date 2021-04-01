#!/usr/bin/env bash
# Arguments:
# 1: next version
# 2: channel

# Update version for Gradle
echo version=$1 >gradle.properties

# Build jars
./gradlew assemble
# Prepare jars
./gradlew publishToSonatype
