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

[[ -f "$legacy_stubs_jar" ]] || {
  echo "legacy Java 8 stubs jar does not exist: $legacy_stubs_jar" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$work_dir"

(cd "$work_dir" && jar xf "$legacy_stubs_jar" \
  java/lang/CharSequence.class \
  java/lang/Iterable.class \
  java/lang/reflect/AnnotatedElement.class \
  java/util/Comparator.class \
  java/util/Iterator.class)

jar cf "$out_jar" -C "$work_dir" .

echo "built $out_jar"
