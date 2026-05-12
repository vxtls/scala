#!/usr/bin/env bash
set -euo pipefail

stage_dir="${1:?stage dir is required}"
repo_url="${MAVEN_REPO_URL:-https://repo1.maven.org/maven2}"
work_dir="$stage_dir/build/source-deps/typesafe-config-0.3.0"
src_dir="$work_dir/src"
classes_dir="$work_dir/classes"
sources_jar="$work_dir/typesafe-config-0.3.0-sources.jar"
out_dir="$stage_dir/lib/extra"
out_jar="$out_dir/typesafe-config-0.3.0.jar"

if [[ -f "$out_jar" ]]; then
  exit 0
fi

mkdir -p "$work_dir" "$src_dir" "$classes_dir" "$out_dir"

if [[ ! -f "$sources_jar" ]]; then
  curl -fsSL "$repo_url/org/skife/com/typesafe/config/typesafe-config/0.3.0/typesafe-config-0.3.0-sources.jar" -o "$sources_jar"
fi

rm -rf "$src_dir" "$classes_dir"
mkdir -p "$src_dir" "$classes_dir"
(cd "$src_dir" && jar xf "$sources_jar")

source_list="$work_dir/sources.list"
find "$src_dir" -name '*.java' | sort > "$source_list"
if [[ ! -s "$source_list" ]]; then
  echo "No Java sources found in $sources_jar" >&2
  exit 1
fi

javac -source 1.5 -target 1.5 -d "$classes_dir" @"$source_list"
jar cf "$out_jar" -C "$classes_dir" .
echo "built $out_jar"
