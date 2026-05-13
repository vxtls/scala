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
base_java8_stubs="${BASE_JAVA8_STUBS:-}"
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
active_starr_lib="$starr_lib"
active_starr_comp="$starr_comp"
[[ -f "$starr_lib" && -f "$starr_comp" ]] || {
  echo "previous stage pack jars are missing under $prev_dir/build/pack/lib" >&2
  exit 1
}

java8_override_jar="$stage_dir/build/java8-charbuffer-overrides.jar"
java8_legacy_stubs_jar="$stage_dir/build/java8-legacy-stubs.jar"
java8_filtered_stubs_jar="$stage_dir/build/java8-filtered-stubs.jar"
java8_partest_boot_stubs_jar="$stage_dir/build/java8-partest-boot-stubs.jar"
java8_buildmanager_boot_stubs_jar="$stage_dir/build/java8-buildmanager-boot-stubs.jar"
legacy_reflect_beans_jar="$stage_dir/build/legacy-reflect-beans.jar"
legacy_beans_meta_jar="$stage_dir/build/legacy-beans-meta.jar"
java_bootclasspath="$java8_override_jar:$java8_legacy_stubs_jar:$java8_filtered_stubs_jar:$rt_jar"
partest_java_cmd="$stage_dir/build/partest-java"
scalac_args="-javabootclasspath $java_bootclasspath"

if grep -q 'name="scalac.args" value="-Xmacros"' "$stage_dir/build.xml"; then
  scalac_args="-Xmacros $scalac_args"
fi

run_ant() {
  local ant_runtime_opts="$ant_opts"
  local runtime_mode="${1:-no}"
  shift || true

  if [[ "$runtime_mode" == "active" ]]; then
    ant_runtime_opts="$ant_runtime_opts -XX:ActiveProcessorCount=1"
  fi
  if [[ "$runtime_mode" == "boot" && -f "$java8_buildmanager_boot_stubs_jar" ]]; then
    ant_runtime_opts="$ant_runtime_opts -Xbootclasspath/p:$java8_buildmanager_boot_stubs_jar"
  fi
  ant_runtime_opts="$ant_runtime_opts -Dpartest.javacmd=$partest_java_cmd"

  (cd "$stage_dir" && env ANT_OPTS="$ant_runtime_opts" "$ant_bin" \
    -Dversion.number="$version_number" \
    -Djava6.home="$JAVA_HOME" \
    -Dlib.starr.jar="$active_starr_lib" \
    -Dcomp.starr.jar="$active_starr_comp" \
    -Dlegacy.reflect.beans.jar="$legacy_reflect_beans_jar" \
    -Dlegacy.beans.meta.jar="$legacy_beans_meta_jar" \
    -Dscalac.args="$scalac_args" \
    -Dpartest.javacmd="$partest_java_cmd" \
    "$@")
}

build_deps() {
  "$script_dir/deps/build-jansi-1.4.sh" "$stage_dir"
  "$script_dir/deps/build-typesafe-config-0.3.0.sh" "$stage_dir"
  "$script_dir/deps/build-legacy-reflect-beans.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-legacy-beans-meta.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-java8-legacy-stubs.sh" "$stage_dir"
  resolve_base_java8_stubs
  "$script_dir/deps/build-filtered-java8-stubs.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-charbuffer-overrides.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-partest-boot-stubs.sh" "$stage_dir" "$java8_legacy_stubs_jar"
  if needs_ground_concrete_transition_compiler; then
    run_ant no jline.done forkjoin.done libs.fjbgpack
    "$script_dir/deps/build-msil-source.sh" "$stage_dir" "$starr_lib" "$starr_comp" "$java_bootclasspath" "$version_number"
    build_ground_concrete_transition_compiler
  elif [[ -d "$stage_dir/src/msil" ]] \
    && grep -q 'msil-source.jar' "$stage_dir/build.xml" \
    && ! grep -q 'scala/tools/nsc/backend/MSILPlatform.scala' "$stage_dir/build.xml"; then
    "$script_dir/deps/build-msil-source.sh" "$stage_dir" "$starr_lib" "$starr_comp" "$java_bootclasspath" "$version_number"
  fi
  if needs_transition_bootstrap_compiler; then
    run_ant no jline.done forkjoin.done libs.fjbgpack
    build_transition_bootstrap_compiler
  fi
  mkdir -p "$stage_dir/build"
cat > "$partest_java_cmd" <<EOF
#!/usr/bin/env bash
exec "$JAVA_HOME/bin/java" "-noverify" "-Xbootclasspath/p:$java8_partest_boot_stubs_jar" "-Dpartest.debug.settings=-javabootclasspath $java_bootclasspath" "\$@"
EOF
  chmod +x "$partest_java_cmd"
}

needs_transition_bootstrap_compiler() {
  [[ "$version_number" == "v2.9.3+55109d-bootstrap" ]]
}

needs_ground_concrete_transition_compiler() {
  [[ "$version_number" == "v2.10.0-M2+46d0d73-bootstrap" ]]
}

build_ground_concrete_transition_compiler() {
  "$script_dir/deps/build-ground-concrete-transition-compiler.sh" \
    "$stage_dir" \
    "$starr_lib" \
    "$starr_comp" \
    "$java_bootclasspath" \
    "$version_number"
  active_starr_lib="$stage_dir/build/transition-ground-concrete/classes/library"
  active_starr_comp="$stage_dir/build/transition-ground-concrete/classes/compiler"
}

build_transition_bootstrap_compiler() {
  local ant_home
  local ant_jar

  ant_home="${ANT_HOME:-}"
  if [[ -z "$ant_home" && -n "$(command -v "$ant_bin" 2>/dev/null)" ]]; then
    ant_home="$(cd "$(dirname "$(command -v "$ant_bin")")/.." && pwd -P)"
  fi
  ant_jar="$ant_home/lib/ant.jar"
  [[ -f "$ant_jar" ]] || {
    echo "ant.jar is required to build the transition bootstrap compiler: $ant_jar" >&2
    exit 1
  }

  "$script_dir/deps/build-transition-bootstrap-compiler.sh" \
    "$stage_dir" \
    "$starr_lib" \
    "$starr_comp" \
    "$java_bootclasspath" \
    "$legacy_reflect_beans_jar" \
    "$legacy_beans_meta_jar" \
    "$ant_jar"
  active_starr_comp="$stage_dir/build/transition-bootstrap-compiler/classes"
}

resolve_base_java8_stubs() {
  if [[ -z "$base_java8_stubs" ]]; then
    base_java8_stubs="$java8_legacy_stubs_jar"
  fi

  if [[ ! -f "$base_java8_stubs" ]]; then
    echo "base Java 8 stubs jar does not exist: $base_java8_stubs" >&2
    exit 1
  fi
}

build_test_deps() {
  "$script_dir/deps/build-partest-jvm-deps.sh" "$stage_dir"
  "$script_dir/deps/build-codelib.sh" "$stage_dir" "$java_bootclasspath"
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
  if grep -q 'name="test.suite.no-buildmanager"' "$stage_dir/build.xml"; then
    run_ant no test.t5293-map.java8
    run_ant no test.suite.no-buildmanager test.continuations.suite
    run_ant boot test.scaladoc
    run_ant boot test.resident.java8
    run_ant boot test.buildmanager.java8
    run_ant active test.scalacheck.java8
  else
    run_ant no test.suite test.continuations.suite
    run_ant boot test.scaladoc
  fi
  run_ant no test.stability
fi
