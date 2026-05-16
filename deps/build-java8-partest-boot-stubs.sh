#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <scala-stage-dir> <legacy-java8-stubs-jar>" >&2
  exit 2
fi

stage_dir="$1"
legacy_stubs_jar="$2"
work_dir="$stage_dir/build/source-deps/java8-partest-boot-stubs"
out_jar="$stage_dir/build/java8-partest-boot-stubs.jar"
buildmanager_out_jar="$stage_dir/build/java8-buildmanager-boot-stubs.jar"

[[ -f "$legacy_stubs_jar" ]] || {
  echo "legacy Java 8 stubs jar does not exist: $legacy_stubs_jar" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$work_dir/runtime" "$work_dir/buildmanager"

(cd "$work_dir/runtime" && jar xf "$legacy_stubs_jar" \
  'java/io/ObjectInputStream$GetField.class' \
  java/io/ObjectInputStream.class \
  java/io/ObjectStreamClass.class \
  java/lang/CharSequence.class \
  java/lang/Iterable.class \
  java/lang/reflect/AnnotatedElement.class \
  java/util/Comparator.class \
  java/util/Iterator.class \
  java/util/concurrent/ConcurrentMap.class)

cp -R "$work_dir/runtime/." "$work_dir/buildmanager/"
(cd "$work_dir/buildmanager" && jar xf "$legacy_stubs_jar" \
  'java/io/ObjectInputStream$GetField.class' \
  java/io/ObjectInputStream.class \
  java/io/ObjectStreamClass.class)

jar cf "$out_jar" -C "$work_dir/runtime" .
jar cf "$buildmanager_out_jar" -C "$work_dir/buildmanager" .

echo "built $out_jar"
echo "built $buildmanager_out_jar"
