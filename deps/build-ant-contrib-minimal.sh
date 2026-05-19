#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <scala-stage-dir>" >&2
  exit 2
fi

stage_dir="$1"
source_url="${ANT_CONTRIB_SOURCE_URL:-https://downloads.sourceforge.net/project/ant-contrib/ant-contrib/1.0b3/ant-contrib-1.0b3-src.zip}"
work_dir="$stage_dir/build/source-deps/ant-contrib-1.0b3"
src_zip="$work_dir/ant-contrib-1.0b3-src.zip"
src_dir="$work_dir/src"
classes_dir="$work_dir/classes"
out_dir="$stage_dir/lib/ant"
out_jar="$out_dir/ant-contrib.jar"
javac_bin="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}/bin/javac"
jar_bin="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}/bin/jar"

ant_home="${ANT_HOME:-}"
if [[ -z "$ant_home" && -n "$(command -v "${ANT_BIN:-ant}" 2>/dev/null)" ]]; then
  ant_home="$(cd "$(dirname "$(command -v "${ANT_BIN:-ant}")")/.." && pwd -P)"
fi
ant_jar="$ant_home/lib/ant.jar"
[[ -f "$ant_jar" ]] || {
  echo "ant.jar is required to build ant-contrib: $ant_jar" >&2
  exit 1
}

mkdir -p "$work_dir" "$src_dir" "$classes_dir" "$out_dir"
if [[ ! -f "$src_zip" ]]; then
  curl -fL -o "$src_zip" "$source_url"
fi

rm -rf "$src_dir" "$classes_dir"
mkdir -p "$src_dir" "$classes_dir"
(cd "$src_dir" && "$jar_bin" xf "$src_zip")

source_root="$src_dir/ant-contrib/src/java"
source_list="$work_dir/sources.list"
{
  echo "$source_root/net/sf/antcontrib/logic/IfTask.java"
  echo "$source_root/net/sf/antcontrib/perf/StopWatch.java"
  echo "$source_root/net/sf/antcontrib/perf/StopWatchTask.java"
} > "$source_list"

"$javac_bin" -source 1.5 -target 1.5 -cp "$ant_jar" -d "$classes_dir" @"$source_list"

mkdir -p "$classes_dir/net/sf/antcontrib"
cat > "$classes_dir/net/sf/antcontrib/antlib.xml" <<'EOF'
<antlib>
  <taskdef name="if" classname="net.sf.antcontrib.logic.IfTask"/>
  <taskdef name="stopwatch" classname="net.sf.antcontrib.perf.StopWatchTask"/>
</antlib>
EOF

"$jar_bin" cf "$out_jar" -C "$classes_dir" .
