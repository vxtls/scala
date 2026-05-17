#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 7 || $# -gt 8 ]]; then
  echo "usage: $0 <scala-stage-dir> <starr-library.jar> <starr-compiler.jar> <java-bootclasspath> <legacy-reflect-beans.jar> <legacy-beans-meta.jar> <ant.jar> [starr-reflect.jar]" >&2
  exit 2
fi

stage_dir="$(cd "$1" && pwd -P)"
starr_lib="$2"
starr_comp="$3"
java_bootclasspath="$4"
legacy_reflect_beans="$5"
legacy_beans_meta="$6"
ant_jar="$7"
starr_reflect="${8:-}"

java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
out_dir="$stage_dir/build/transition-bootstrap-compiler/classes"
work_dir="$stage_dir/build/transition-bootstrap-compiler"
sources="$work_dir/compiler-sources.list"

for required in "$starr_lib" "$starr_comp" "$legacy_reflect_beans" "$legacy_beans_meta" "$ant_jar"; do
  [[ -e "$required" ]] || {
    echo "required transition compiler input is missing: $required" >&2
    exit 1
  }
done

rm -rf "$work_dir"
mkdir -p "$out_dir"
find "$stage_dir/src/compiler" -name '*.scala' | sort > "$sources"

classpath_items=(
  "$out_dir"
  "$starr_lib"
  "$legacy_reflect_beans"
  "$legacy_beans_meta"
  "$stage_dir/build/libs/fjbg.jar"
  "$stage_dir/build/libs/forkjoin.jar"
  "$stage_dir/build/libs/jline.jar"
  "$stage_dir/lib/extra/msil-source.jar"
  "$ant_jar"
)

classpath=""
for item in "${classpath_items[@]}"; do
  if [[ -e "$item" ]]; then
    if [[ -z "$classpath" ]]; then
      classpath="$item"
    else
      classpath="$classpath:$item"
    fi
  fi
done

scalac_boot_cp="$starr_comp:$starr_lib"
if [[ -n "$starr_reflect" && -e "$starr_reflect" ]]; then
  scalac_boot_cp="$scalac_boot_cp:$starr_reflect"
fi

"$java_bin" -Xmx1536M -cp "$scalac_boot_cp" scala.tools.nsc.Main \
  -javabootclasspath "$java_bootclasspath" \
  -classpath "$classpath" \
  -d "$out_dir" \
  @"$sources"

while IFS= read -r resource; do
  rel="${resource#$stage_dir/src/compiler/}"
  mkdir -p "$out_dir/$(dirname "$rel")"
  cp "$resource" "$out_dir/$rel"
done < <(find "$stage_dir/src/compiler" -type f ! -name '*.scala' | sort)

echo "built $out_dir"
