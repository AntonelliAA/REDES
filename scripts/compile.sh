#!/usr/bin/env bash
set -euo pipefail

rm -rf out
mkdir -p out/main out/test
javac --release 17 -encoding UTF-8 -d out/main $(find src -name '*.java' -print)
javac --release 17 -encoding UTF-8 -cp out/main -d out/test $(find test -name '*.java' -print)
