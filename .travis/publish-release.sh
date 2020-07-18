#!/usr/bin/env bash
# Arguments:
# 1: next version
# 2: channel

# publish release
./gradlew bintrayUpload
./gradlew bintrayPublish
