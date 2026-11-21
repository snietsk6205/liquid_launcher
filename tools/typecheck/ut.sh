#!/bin/bash
# Compiles core/common, core/settings, core/data and feature/redirect with their tests and runs JUnit on them.
# Usage: ut.sh [TestClassSimpleName ...]   (default: every test of those modules)
cd "$(dirname "$0")"
REPO=$(cd "$(dirname "$0")/../.." && pwd)
CP=$(ls lib/*.jar | tr '\n' ':')
MODS="core/common core/settings core/data feature/redirect"
SRC=""
for m in $MODS; do SRC="$SRC $(find $REPO/$m/src/main/kotlin $REPO/$m/src/test/kotlin -name '*.kt' 2>/dev/null | tr '\n' ' ')"; done
rm -rf utout; mkdir -p utout
java -Xmx4g -cp "kc/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 21 -nowarn \
  -cp "$CP" -Xplugin=kc/kotlin-compose-compiler-plugin-embeddable-2.4.10.jar \
  -Xplugin=kc/kotlin-serialization-compiler-plugin-embeddable-2.4.10.jar \
  -d utout stubs/*.kt $SRC > ut-compile.txt 2>&1 || { grep -E "error:" ut-compile.txt | head -30; echo "COMPILE FAILED"; exit 1; }
if [ $# -gt 0 ]; then
  CLASSES=$(for n in "$@"; do find utout -name "$n.class" | sed 's#utout/##; s#\.class$##; s#/#.#g'; done)
else
  CLASSES=$(find utout -name '*Test.class' | sed 's#utout/##; s#\.class$##; s#/#.#g')
fi
HERE=$(pwd)
RUNCP="$HERE/utout:$(ls -d $REPO/*/*/src/test/resources 2>/dev/null | tr "\n" ":"):$(ls $HERE/lib/*.jar | tr '\n' ':')"
cd $REPO/core/common && java -cp "$RUNCP" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v "JAVA_TOOL_OPTIONS" | grep -vE "^\s+at (org\.junit|java\.|jdk\.|sun\.)" | tail -40
