#!/bin/bash
# Downloads a fixed jar set from Maven Central for the local type-check harness.
set -u
cd "$(dirname "$0")"
mkdir -p lib kc
M=https://repo.maven.apache.org/maven2
get() { # group artifact version dir [ext]
  local g=${1//.//} a=$2 v=$3 d=$4 e=${5:-jar}
  local f="$d/$a-$v.$e"
  [ -s "$f" ] && return 0
  for i in 1 2 3 4 5 6 7 8 9 10; do
    code=$(curl -s -w '%{http_code}' -o "$f.part" --max-time 300 "$M/$g/$a/$v/$a-$v.$e")
    if [ "$code" = 200 ]; then mv "$f.part" "$f"; echo "ok $a-$v"; return 0; fi
    rm -f "$f.part"; sleep $((i*4))
  done
  echo "FAILED $a-$v ($code)"; return 1
}
K=2.4.10
# compiler
get org.jetbrains.kotlin kotlin-compiler-embeddable $K kc
get org.jetbrains.kotlin kotlin-stdlib $K kc
get org.jetbrains.kotlin kotlin-script-runtime $K kc
get org.jetbrains.kotlin kotlin-reflect 1.6.10 kc
get org.jetbrains.kotlin kotlin-daemon-embeddable $K kc
get org.jetbrains.intellij.deps trove4j 1.0.20200330 kc
get org.jetbrains annotations 13.0 kc
get org.jetbrains.kotlinx kotlinx-coroutines-core-jvm 1.8.0 kc
get org.jetbrains.kotlin kotlin-compose-compiler-plugin-embeddable $K kc
get org.jetbrains.kotlin kotlin-serialization-compiler-plugin-embeddable $K kc
# project classpath
get org.jetbrains.kotlin kotlin-stdlib $K lib
get org.jetbrains.kotlinx kotlinx-coroutines-core-jvm 1.11.0 lib
get org.jetbrains.kotlinx kotlinx-serialization-core-jvm 1.13.0 lib
get org.jetbrains.kotlinx kotlinx-serialization-json-jvm 1.13.0 lib
get junit junit 4.13.2 lib
get org.hamcrest hamcrest-core 1.3 lib
C=1.8.2
for p in runtime:runtime-desktop runtime:runtime-saveable-desktop ui:ui-desktop ui:ui-geometry-desktop ui:ui-graphics-desktop \
         ui:ui-text-desktop ui:ui-unit-desktop ui:ui-util-desktop ui:ui-backhandler-desktop foundation:foundation-desktop \
         foundation:foundation-layout-desktop animation:animation-desktop animation:animation-core-desktop \
         material3:material3-desktop material:material-ripple-desktop; do
  get org.jetbrains.compose.${p%%:*} ${p##*:} $C lib
done
get org.jetbrains.skiko skiko-awt 0.9.4.2 lib
get io.github.kyant0 backdrop-desktop 2.0.1 lib
get org.robolectric android-all 17-robolectric-16653203 lib
# 0.16.0: Media3 (video wallpapers) is on Google's Maven only, as .aar files: their classes.jar is what the check needs.
G=https://dl.google.com/android/maven2
for a in media3-common media3-exoplayer media3-transformer media3-effect media3-datasource media3-decoder media3-container media3-extractor media3-muxer; do
  f="lib/$a-1.11.1.jar"
  [ -s "$f" ] && continue
  if curl -s --max-time 300 -o "/tmp/$a.aar" "$G/androidx/media3/$a/1.11.1/$a-1.11.1.aar"; then
    unzip -p "/tmp/$a.aar" classes.jar > "$f" && echo "ok $a-1.11.1" || echo "FAILED $a"
  else
    echo "FAILED $a"
  fi
done
echo done
