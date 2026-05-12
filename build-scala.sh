#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "usage: $0 <stage-dir> <previous-stage-dir> <build|test|all> [version-number]" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
stage_dir="$(cd "$1" && pwd -P)"
prev_dir="$(cd "$2" && pwd -P)"
mode="$3"
version_number="${4:-$(basename "$stage_dir" | sed 's/-bootstrap$//')}"
ant_bin="${ANT_BIN:-ant}"
ant_opts="${ANT_OPTS:--Xmx1536M}"
base_java8_stubs="${BASE_JAVA8_STUBS:-$script_dir/../v2.9.3-bootstrap/build/java8-stubs-r9.jar}"
rt_jar="${JAVA_HOME:+$JAVA_HOME/jre/lib/rt.jar}"

[[ "$mode" == "build" || "$mode" == "test" || "$mode" == "all" ]] || {
  echo "mode must be build, test, or all" >&2
  exit 2
}
[[ -n "${JAVA_HOME:-}" && -f "$rt_jar" ]] || {
  echo "JAVA_HOME must point at a JDK 8 installation" >&2
  exit 1
}

starr_lib="$prev_dir/build/pack/lib/scala-library.jar"
starr_comp="$prev_dir/build/pack/lib/scala-compiler.jar"
[[ -f "$starr_lib" && -f "$starr_comp" ]] || {
  echo "previous stage pack jars are missing under $prev_dir/build/pack/lib" >&2
  exit 1
}

java8_override_jar="$stage_dir/build/java8-charbuffer-overrides.jar"
java8_legacy_stubs_jar="$stage_dir/build/java8-legacy-stubs.jar"
java8_filtered_stubs_jar="$stage_dir/build/java8-filtered-stubs.jar"
java8_partest_boot_stubs_jar="$stage_dir/build/java8-partest-boot-stubs.jar"
legacy_reflect_beans_jar="$stage_dir/build/legacy-reflect-beans.jar"
legacy_beans_meta_jar="$stage_dir/build/legacy-beans-meta.jar"
java_bootclasspath="$java8_override_jar:$java8_legacy_stubs_jar:$java8_filtered_stubs_jar:$rt_jar"
partest_java_cmd="$stage_dir/build/partest-java"

run_ant() {
  local ant_runtime_opts="$ant_opts"
  local include_ant_boot="${1:-no}"
  shift || true

  if [[ "$include_ant_boot" == "yes" && -f "$java8_partest_boot_stubs_jar" ]]; then
    ant_runtime_opts="$ant_runtime_opts -Xbootclasspath/p:$java8_partest_boot_stubs_jar"
  fi
  ant_runtime_opts="$ant_runtime_opts -Dpartest.javacmd=$partest_java_cmd"

  (cd "$stage_dir" && env ANT_OPTS="$ant_runtime_opts" "$ant_bin" \
    -Dversion.number="$version_number" \
    -Dlib.starr.jar="$starr_lib" \
    -Dcomp.starr.jar="$starr_comp" \
    -Dlegacy.beans.meta.jar="$legacy_beans_meta_jar" \
    -Dscalac.args="-javabootclasspath $java_bootclasspath" \
    -Dpartest.javacmd="$partest_java_cmd" \
    "$@")
}

build_deps() {
  "$script_dir/deps/build-jansi-1.4.sh" "$stage_dir"
  "$script_dir/deps/build-legacy-reflect-beans.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-legacy-beans-meta.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-java8-legacy-stubs.sh" "$stage_dir"
  "$script_dir/deps/build-filtered-java8-stubs.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-charbuffer-overrides.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-partest-boot-stubs.sh" "$stage_dir" "$java8_legacy_stubs_jar"
  mkdir -p "$stage_dir/build"
cat > "$partest_java_cmd" <<EOF
#!/usr/bin/env bash
exec "$JAVA_HOME/bin/java" "-noverify" "-Xbootclasspath/p:$java8_partest_boot_stubs_jar" "-Dpartest.debug.settings=-javabootclasspath $java_bootclasspath" "\$@"
EOF
  chmod +x "$partest_java_cmd"
  if [[ -d "$stage_dir/src/msil" ]]; then
    "$script_dir/deps/build-msil-source.sh" "$stage_dir" "$starr_lib" "$starr_comp" "$java_bootclasspath" "$version_number"
  fi
}

build_test_deps() {
  "$script_dir/deps/build-partest-jvm-deps.sh" "$stage_dir"
  if [[ -d "$stage_dir/test/instrumented" ]]; then
    "$script_dir/deps/build-instrumented-speclib.sh" "$stage_dir" "$java_bootclasspath"
  fi
}

if [[ "$mode" == "build" || "$mode" == "all" ]]; then
  run_ant no locker.clean clean
  build_deps
  run_ant no build
fi

if [[ "$mode" == "test" || "$mode" == "all" ]]; then
  build_deps
  build_test_deps
  run_ant yes test.suite test.continuations.suite test.scaladoc
  run_ant no test.stability
fi
