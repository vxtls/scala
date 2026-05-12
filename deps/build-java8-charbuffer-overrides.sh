#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <scala-stage-dir> <base-java8-stubs-jar>" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
stage_dir="$1"
base_stubs_jar="$2"
src_dir="$script_dir/java8-overrides"
work_dir="$stage_dir/build/source-deps/java8-charbuffer-overrides"
classes_dir="$work_dir/classes"
out_jar="$stage_dir/build/java8-charbuffer-overrides.jar"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
rt_jar="${JAVA_HOME:+$JAVA_HOME/jre/lib/rt.jar}"

[[ -d "$stage_dir" ]] || {
  echo "stage directory does not exist: $stage_dir" >&2
  exit 1
}
[[ -f "$base_stubs_jar" ]] || {
  echo "base Java 8 stubs jar does not exist: $base_stubs_jar" >&2
  exit 1
}
[[ -n "$rt_jar" && -f "$rt_jar" ]] || {
  echo "JAVA_HOME must point at a JDK 8 installation" >&2
  exit 1
}

rm -rf "$classes_dir"
mkdir -p "$classes_dir"

"$javac_bin" -source 1.5 -target 1.5 -XDignore.symbol.file \
  -bootclasspath "$base_stubs_jar:$rt_jar" \
  -d "$classes_dir" \
  $(find "$src_dir" -name '*.java' | sort)

jar cf "$out_jar" -C "$classes_dir" .

echo "built $out_jar"
