#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <scala-stage-dir>" >&2
  exit 2
fi

stage_dir="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
src_dir="$script_dir/java8-legacy-stubs-src"
work_dir="$stage_dir/build/source-deps/java8-legacy-stubs"
classes_dir="$work_dir/classes"
out_jar="$stage_dir/build/java8-legacy-stubs.jar"

[[ -d "$src_dir" ]] || {
  echo "Java 8 legacy stub sources are missing: $src_dir" >&2
  exit 1
}
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]] || {
  echo "JAVA_HOME must point at a JDK 8 installation" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$classes_dir"

find "$src_dir" -name '*.java' -print | sort > "$work_dir/sources.list"
"$JAVA_HOME/bin/javac" -source 1.6 -target 1.6 -d "$classes_dir" @"$work_dir/sources.list"
jar cf "$out_jar" -C "$classes_dir" .

echo "built $out_jar"
