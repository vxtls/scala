#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <scala-stage-dir>" >&2
  exit 2
fi

stage_dir="$1"
repo_url="${MAVEN_REPO_URL:-https://repo1.maven.org/maven2}"
work_dir="$stage_dir/build/source-deps/diffutils-1.3.0"
src_dir="$work_dir/src"
classes_dir="$work_dir/classes"
sources_jar="$work_dir/diffutils-1.3.0-sources.jar"
out_dir="$stage_dir/lib/extra"
out_jar="$out_dir/diffutils-1.3.0.jar"
javac_bin="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}/bin/javac"
jar_bin="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}/bin/jar"

if [[ -f "$out_jar" ]]; then
  exit 0
fi

mkdir -p "$work_dir" "$src_dir" "$classes_dir" "$out_dir"
if [[ ! -f "$sources_jar" ]]; then
  curl -fL -o "$sources_jar" "$repo_url/com/googlecode/java-diff-utils/diffutils/1.3.0/diffutils-1.3.0-sources.jar"
fi

rm -rf "$src_dir" "$classes_dir"
mkdir -p "$src_dir" "$classes_dir"
(cd "$src_dir" && "$jar_bin" xf "$sources_jar")

find "$src_dir" -name '*.java' | sort > "$work_dir/sources.list"
if [[ ! -s "$work_dir/sources.list" ]]; then
  echo "No Java sources found in $sources_jar" >&2
  exit 1
fi

"$javac_bin" -source 1.5 -target 1.5 -d "$classes_dir" @"$work_dir/sources.list"
"$jar_bin" cf "$out_jar" -C "$classes_dir" .
echo "built $out_jar"
