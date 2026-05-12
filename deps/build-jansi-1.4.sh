#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <scala-stage-dir>" >&2
  exit 2
fi

stage_dir="$1"
repo_url="https://repo1.maven.org/maven2"
work_dir="$stage_dir/build/source-deps/jansi-1.4"
src_dir="$work_dir/src"
classes_dir="$work_dir/classes"
out_dir="$stage_dir/lib/extra"
out_jar="$out_dir/jansi-1.4.jar"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

download_source_jar() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local jar="$work_dir/${artifact}-${version}-sources.jar"

  if [[ ! -f "$jar" ]]; then
    curl -fL -o "$jar" \
      "$repo_url/$group_path/$artifact/$version/${artifact}-${version}-sources.jar"
  fi

  (cd "$src_dir" && jar xf "$jar")
}

[[ -d "$stage_dir" ]] || {
  echo "stage directory does not exist: $stage_dir" >&2
  exit 1
}

mkdir -p "$src_dir" "$classes_dir" "$out_dir"
rm -rf "$src_dir" "$classes_dir"
mkdir -p "$src_dir" "$classes_dir"

download_source_jar "org/fusesource/jansi" "jansi" "1.4"
download_source_jar "org/fusesource/jansi" "jansi-native" "1.1"
download_source_jar "org/fusesource/hawtjni" "hawtjni-runtime" "1.1"

find "$src_dir" -name '*.java' | sort > "$work_dir/sources.list"

"$javac_bin" -source 1.5 -target 1.5 -XDignore.symbol.file \
  -d "$classes_dir" \
  @"$work_dir/sources.list"

jar cf "$out_jar" -C "$classes_dir" .

echo "built $out_jar"
