#!/usr/bin/env bash
set -euo pipefail

bash scripts/compile.sh
for test_file in $(find test -name '*Test.java' -print); do
    test_class=${test_file#test/}
    test_class=${test_class%.java}
    test_class=${test_class//\//.}
    java -ea -cp out/main:out/test "$test_class"
done

