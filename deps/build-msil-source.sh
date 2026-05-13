#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 5 ]]; then
  echo "usage: $0 <scala-stage-dir> <starr-lib-jar> <starr-compiler-jar> <java-bootclasspath> <scala-version>" >&2
  exit 2
fi

stage_dir="$1"
starr_lib_jar="$2"
starr_comp_jar="$3"
java_bootclasspath="$4"
scala_version="$5"
src_dir="$stage_dir/src/msil"
work_dir="$stage_dir/build/source-deps/msil-source"
classes_dir="$work_dir/classes"
out_dir="$stage_dir/lib/extra"
out_jar="$out_dir/msil-source.jar"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
compiler_cp="$starr_comp_jar:$starr_lib_jar"

[[ -d "$src_dir" ]] || {
  echo "vendored MSIL sources not found: $src_dir" >&2
  exit 1
}
[[ -f "$starr_lib_jar" && -e "$starr_comp_jar" ]] || {
  echo "starr jars are required to build vendored MSIL Scala sources" >&2
  exit 1
}

rm -rf "$classes_dir"
mkdir -p "$classes_dir" "$out_dir"

for runtime_jar in "$stage_dir/build/libs/fjbg.jar" "$stage_dir/build/libs/forkjoin.jar" "$stage_dir/build/libs/jline.jar"; do
  if [[ -f "$runtime_jar" ]]; then
    compiler_cp="$compiler_cp:$runtime_jar"
  fi
done

find "$src_dir" -name '*.java' \
  ! -path '*/tests/*' \
  | sort > "$work_dir/java-sources.list"

"$javac_bin" -source 1.5 -target 1.5 -XDignore.symbol.file \
  -d "$classes_dir" \
  @"$work_dir/java-sources.list"

find "$src_dir" -name '*.scala' \
  ! -path '*/tests/*' \
  | sort > "$work_dir/scala-sources.list"

if [[ -s "$work_dir/scala-sources.list" ]]; then
  "$java_bin" -cp "$compiler_cp" scala.tools.nsc.Main \
    -javabootclasspath "$java_bootclasspath" \
    -classpath "$classes_dir:$starr_lib_jar" \
    -d "$classes_dir" \
    @"$work_dir/scala-sources.list"
fi

jar cf "$out_jar" -C "$classes_dir" .

echo "built $out_jar for $scala_version"
