#!/bin/bash
# Generates an R stub for core/designsystem from its drawables (the only module code that uses its own R).
REPO=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(dirname "$0")/stubs/RStub.kt
{
  echo "@file:Suppress(\"unused\")"
  echo "package dev.liquid.launcher.core.designsystem"
  echo "object R { object drawable {"
  ls $REPO/core/designsystem/src/main/res/drawable | sed -E 's/\.(xml|png|webp)$//' | sort -u | awk '{printf "  const val %s = 0\n", $1}'
  echo "} }"
} > "$OUT"
