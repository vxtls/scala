#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <scala-stage-dir> <java-bootclasspath>" >&2
  exit 2
fi

stage_dir="$1"
java_bootclasspath="$2"
src_dir="$stage_dir/test/instrumented/library"
classes_dir="$stage_dir/build/source-deps/instrumented-speclib/classes"
out_dir="$stage_dir/test/files/speclib"
out_jar="$out_dir/instrumented.jar"
out_library="$out_dir/scala-library.jar"
scalac_bin="$stage_dir/build/pack/bin/scalac"
scala_lib="$stage_dir/build/pack/lib/scala-library.jar"
javac_bin="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}/bin/javac"

[[ -x "$scalac_bin" ]] || {
  echo "scala pack compiler does not exist: $scalac_bin" >&2
  exit 1
}
[[ -f "$scala_lib" ]] || {
  echo "scala pack library does not exist: $scala_lib" >&2
  exit 1
}
[[ -d "$src_dir" ]] || {
  echo "instrumented speclib sources do not exist: $src_dir" >&2
  exit 1
}

rm -rf "$classes_dir"
mkdir -p "$classes_dir" "$out_dir"

scala_sources=()
while IFS= read -r source_file; do
  scala_sources+=("$source_file")
done < <(find "$src_dir" -name '*.scala' -print | sort)

java_sources=()
while IFS= read -r source_file; do
  java_sources+=("$source_file")
done < <(find "$src_dir" -name '*.java' -print | sort)

"$scalac_bin" \
  -javabootclasspath "$java_bootclasspath" \
  -classpath "$scala_lib" \
  -d "$classes_dir" \
  "${scala_sources[@]}" "${java_sources[@]}"

if [[ ${#java_sources[@]} -gt 0 ]]; then
  "$javac_bin" -source 6 -target 6 \
    -bootclasspath "$java_bootclasspath" \
    -classpath "$scala_lib:$classes_dir" \
    -d "$classes_dir" \
    "${java_sources[@]}"
fi

rm -f "$out_jar"
(cd "$classes_dir" && jar cf "$out_jar" .)

cp "$scala_lib" "$out_library"
(cd "$classes_dir" && jar uf "$out_library" scala/runtime/BoxesRunTime.class scala/runtime/ScalaRunTime*.class)

echo "built $out_jar"
echo "built $out_library"
