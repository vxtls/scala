#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "usage: $0 <stage-dir> <previous-stage-dir> <build|test|all|locker-pack> [version-number]" >&2
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

[[ "$mode" == "build" || "$mode" == "test" || "$mode" == "all" || "$mode" == "locker-pack" ]] || {
  echo "mode must be build, test, all, or locker-pack" >&2
  exit 2
}
[[ -n "${JAVA_HOME:-}" && -f "$rt_jar" ]] || {
  echo "JAVA_HOME must point at a JDK 8 installation" >&2
  exit 1
}

starr_lib="$prev_dir/build/pack/lib/scala-library.jar"
starr_comp="$prev_dir/build/pack/lib/scala-compiler.jar"
starr_reflect="$prev_dir/build/pack/lib/scala-reflect.jar"
[[ -f "$starr_reflect" ]] || starr_reflect=""
[[ -f "$starr_lib" && -f "$starr_comp" ]] || {
  echo "previous stage pack jars are missing under $prev_dir/build/pack/lib" >&2
  exit 1
}
active_starr_comp="$starr_comp"
prev_forkjoin_jar="$prev_dir/build/libs/forkjoin.jar"
fallback_forkjoin_stage_dir="$(dirname "$stage_dir")/v2.10.0-M3+8ce4787-bootstrap"

java8_override_jar="$stage_dir/build/java8-charbuffer-overrides.jar"
java8_legacy_stubs_jar="$stage_dir/build/java8-legacy-stubs.jar"
java8_filtered_stubs_jar="$stage_dir/build/java8-filtered-stubs.jar"
java8_partest_boot_stubs_jar="$stage_dir/build/java8-partest-boot-stubs.jar"
java8_buildmanager_boot_stubs_jar="$stage_dir/build/java8-buildmanager-boot-stubs.jar"
legacy_reflect_beans_jar="$stage_dir/build/legacy-reflect-beans.jar"
legacy_beans_meta_jar="$stage_dir/build/legacy-beans-meta.jar"
java_bootclasspath="$java8_override_jar:$java8_legacy_stubs_jar:$java8_filtered_stubs_jar:$rt_jar"
partest_java_cmd="$stage_dir/build/partest-java"
partest_debug_java_cmd="$stage_dir/build/partest-debug-java"
partest_boot_java_cmd="$stage_dir/build/partest-boot-java"
partest_icode_java_cmd="$stage_dir/build/partest-icode-java"
scalac_args="-javabootclasspath $java_bootclasspath"

if grep -q 'name="scalac.args" value="-Xmacros"' "$stage_dir/build.xml"; then
  scalac_args="-Xmacros $scalac_args"
fi

if [[ "$version_number" == "v2.10.0-M7+a0a63c-bootstrap" ]]; then
  scalac_args="-Xno-patmat-analysis $scalac_args"
fi

run_ant() {
  local ant_runtime_opts="$ant_opts"
  local runtime_mode="${1:-no}"
  local prev_forkjoin_arg=""
  local current_partest_java_cmd="$partest_java_cmd"
  shift || true

  if [[ "$runtime_mode" == "active" ]]; then
    ant_runtime_opts="$ant_runtime_opts -XX:ActiveProcessorCount=1"
  fi
  if [[ "$runtime_mode" == "boot" && -f "$java8_buildmanager_boot_stubs_jar" ]]; then
    ant_runtime_opts="$ant_runtime_opts -Xbootclasspath/p:$java8_buildmanager_boot_stubs_jar"
  fi
  if [[ "$runtime_mode" == "java8boot" ]]; then
    current_partest_java_cmd="$partest_boot_java_cmd"
  fi
  if [[ "$runtime_mode" == "debug" ]]; then
    current_partest_java_cmd="$partest_debug_java_cmd"
  fi
  if [[ "$runtime_mode" == "icode" ]]; then
    current_partest_java_cmd="$partest_icode_java_cmd"
  fi
  ant_runtime_opts="$ant_runtime_opts -Dpartest.javacmd=$current_partest_java_cmd"

  if needs_previous_forkjoin_jar; then
    if [[ ! -f "$prev_forkjoin_jar" && -f "$fallback_forkjoin_stage_dir/build/libs/forkjoin.jar" ]]; then
      prev_forkjoin_jar="$fallback_forkjoin_stage_dir/build/libs/forkjoin.jar"
    fi
    [[ -f "$prev_forkjoin_jar" ]] || {
      echo "previous stage forkjoin jar is missing: $prev_forkjoin_jar" >&2
      exit 1
    }
    prev_forkjoin_arg="-Dforkjoin.jar=$prev_forkjoin_jar"
  fi

  (cd "$stage_dir" && env ANT_OPTS="$ant_runtime_opts" "$ant_bin" \
    -Dversion.number="$version_number" \
    -Djava6.home="$JAVA_HOME" \
    -Dlib.starr.jar="$starr_lib" \
    ${starr_reflect:+"-Dreflect.starr.jar=$starr_reflect"} \
    -Dcomp.starr.jar="$active_starr_comp" \
    -Dlegacy.reflect.beans.jar="$legacy_reflect_beans_jar" \
    -Dlegacy.beans.meta.jar="$legacy_beans_meta_jar" \
    ${prev_forkjoin_arg:+"$prev_forkjoin_arg"} \
    -Dscalac.args="$scalac_args" \
    -Djava8.partest.scalac.args="-javabootclasspath $java_bootclasspath" \
    -Dpartest.javacmd="$current_partest_java_cmd" \
    "$@")
}

build_deps() {
  "$script_dir/deps/build-jansi-1.4.sh" "$stage_dir"
  if grep -q 'typesafe-config-0.4.0.jar' "$stage_dir/build.xml"; then
    "$script_dir/deps/build-typesafe-config-0.4.0.sh" "$stage_dir"
  else
    "$script_dir/deps/build-typesafe-config-0.3.0.sh" "$stage_dir"
  fi
  "$script_dir/deps/build-legacy-reflect-beans.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-legacy-beans-meta.sh" "$stage_dir" "$starr_lib"
  "$script_dir/deps/build-java8-legacy-stubs.sh" "$stage_dir"
  resolve_base_java8_stubs
  "$script_dir/deps/build-filtered-java8-stubs.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-charbuffer-overrides.sh" "$stage_dir" "$base_java8_stubs"
  "$script_dir/deps/build-java8-partest-boot-stubs.sh" "$stage_dir" "$java8_legacy_stubs_jar"
  if [[ -d "$stage_dir/src/msil" ]] \
    && grep -q 'msil-source.jar' "$stage_dir/build.xml" \
    && ! grep -q 'scala/tools/nsc/backend/MSILPlatform.scala' "$stage_dir/build.xml"; then
    "$script_dir/deps/build-msil-source.sh" "$stage_dir" "$starr_lib" "$starr_comp" "$java_bootclasspath" "$version_number"
  fi
  if needs_transition_bootstrap_compiler; then
    run_ant no jline.done forkjoin.done libs.fjbgpack
    build_transition_bootstrap_compiler
  fi
  mkdir -p "$stage_dir/build"
cat > "$partest_java_cmd" <<'EOF'
#!/usr/bin/env bash
set -e
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
java_home="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}"
clean_args=()
skip_next=0
for arg in "$@"; do
  if [[ "$skip_next" == "1" ]]; then
    skip_next=0
    continue
  fi
  case "$arg" in
    -Dpartest.debug.settings=-javabootclasspath)
      skip_next=1
      ;;
    -Dpartest.debug.settings*) ;;
    *) clean_args+=("$arg") ;;
  esac
done
exec "$java_home/bin/java" \
  "-noverify" \
  "-Xbootclasspath/p:$script_dir/java8-partest-boot-stubs.jar" \
  "${clean_args[@]}"
EOF
  chmod +x "$partest_java_cmd"
cat > "$partest_boot_java_cmd" <<'EOF'
#!/usr/bin/env bash
set -e
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
java_home="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}"
exec "$java_home/bin/java" \
  "-noverify" \
  "-Xbootclasspath/p:$script_dir/java8-buildmanager-boot-stubs.jar" \
  "$@"
EOF
  chmod +x "$partest_boot_java_cmd"
cat > "$partest_debug_java_cmd" <<'EOF'
#!/usr/bin/env bash
set -e
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
java_home="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}"
java_bootclasspath="$script_dir/java8-charbuffer-overrides.jar:$script_dir/java8-legacy-stubs.jar:$script_dir/java8-filtered-stubs.jar:$java_home/jre/lib/rt.jar"
exec "$java_home/bin/java" \
  "-noverify" \
  "-Xbootclasspath/p:$script_dir/java8-partest-boot-stubs.jar" \
  "-Dpartest.debug.settings=-javabootclasspath \"$java_bootclasspath\"" \
  "$@"
EOF
  chmod +x "$partest_debug_java_cmd"
cat > "$partest_icode_java_cmd" <<'EOF'
#!/usr/bin/env bash
set -e
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
java_home="${JAVA_HOME:?JAVA_HOME must point at a JDK 8 installation}"
rt_jar="$java_home/jre/lib/rt.jar"
java_bootclasspath="$script_dir/java8-charbuffer-overrides.jar:$script_dir/java8-legacy-stubs.jar:$script_dir/java8-filtered-stubs.jar:$rt_jar"
exec "$java_home/bin/java" \
  "-noverify" \
  "-Xbootclasspath/p:$script_dir/java8-buildmanager-boot-stubs.jar" \
  "-Dpartest.debug.settings=-javabootclasspath \"$java_bootclasspath\"" \
  "$@"
EOF
  chmod +x "$partest_icode_java_cmd"
}

needs_transition_bootstrap_compiler() {
  [[ "$version_number" == "v2.9.3+55109d-bootstrap" ]]
}

needs_anyval_class_transition() {
  [[ "$version_number" == "v2.10.0-M2+be11c92-bootstrap" ]]
}

needs_compiler_first_transition() {
  [[ "$version_number" == "v2.10.0-M3+1708a7f-bootstrap" ]]
}

needs_arraytag_transition() {
  [[ "$version_number" == "v2.10.0-M3+bc5f42f-bootstrap" ]]
}

needs_classtag_string_transition() {
  [[ "$version_number" == "v2.10.0-M4+3becbd5-bootstrap" ]]
}

needs_previous_forkjoin_jar() {
  [[ "$version_number" == "v2.10.0-M3+6bb5975-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+252a448-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+bdff66e-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+ce67870-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+6355d1-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+3896a4-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+d9103e-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+0b2f1bc-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+07f7baa-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+bc5f42f-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+bc5f42f+5acac4d-bootstrap" ]] \
    || [[ "$version_number" == "v2.10.0-M3+5acac4d-bootstrap" ]]
}

build_anyval_class_transition() {
  build_compiler_first_transition
}

build_bootstrap_lib_jars() {
  if grep -q 'target name="libs.fjbgpack"' "$stage_dir/build.xml"; then
    run_ant no jline.done forkjoin.done libs.fjbgpack
  else
    run_ant no jline.done forkjoin.done fjbg.done
  fi
}

build_compiler_first_transition() {
  local transition_comp="$stage_dir/build/locker/classes/compiler"
  local seeded_lib="$stage_dir/build/locker/classes/library"

  build_bootstrap_lib_jars

  rm -rf "$seeded_lib" "$stage_dir/build/locker/library.complete"
  mkdir -p "$seeded_lib"
  (cd "$seeded_lib" && "$JAVA_HOME/bin/jar" xf "$starr_lib")
  touch "$stage_dir/build/locker/library.complete"

  active_starr_comp="$starr_comp"
  run_ant no locker.comp

  rm -rf "$seeded_lib" "$stage_dir/build/locker/library.complete"
  active_starr_comp="$transition_comp"
  run_ant no locker.lib
  run_ant no build
}

build_arraytag_transition() {
  local transition_dir="$stage_dir/build/transition-starr"
  local seeded_lib="$stage_dir/build/locker/classes/library"

  build_bootstrap_lib_jars

  rm -rf "$stage_dir/build/locker" "$transition_dir"
  mkdir -p "$seeded_lib"
  (cd "$seeded_lib" && "$JAVA_HOME/bin/jar" xf "$starr_lib")
  touch "$stage_dir/build/locker/library.complete"

  run_ant no locker.reflect locker.comp

  mkdir -p "$transition_dir/classes"
  cp -R "$stage_dir/build/locker/classes/library" "$transition_dir/classes/library"
  cp -R "$stage_dir/build/locker/classes/reflect" "$transition_dir/classes/reflect"
  cp -R "$stage_dir/build/locker/classes/compiler" "$transition_dir/classes/compiler"

  rm -rf "$stage_dir/build/locker" "$stage_dir/build/quick" "$stage_dir/build/pack" "$stage_dir/build/strap" "$stage_dir/build/palo"
  starr_lib="$transition_dir/classes/library"
  starr_reflect="$transition_dir/classes/reflect"
  active_starr_comp="$transition_dir/classes/compiler"
  run_ant no build
}

copy_starr_classtag_classes() {
  local target_lib="$1"
  local class_tag_dir="$stage_dir/build/starr-classtag"

  rm -rf "$class_tag_dir"
  mkdir -p "$class_tag_dir"
  (
    cd "$class_tag_dir"
    "$JAVA_HOME/bin/jar" xf "$starr_lib" $("$JAVA_HOME/bin/jar" tf "$starr_lib" | grep '^scala/reflect/ClassTag')
  )
  mkdir -p "$target_lib/scala/reflect"
  cp "$class_tag_dir"/scala/reflect/ClassTag*.class "$target_lib/scala/reflect/"
}

build_classtag_string_transition() {
  local transition_dir="$stage_dir/build/transition-starr"
  local seeded_lib="$stage_dir/build/locker/classes/library"

  build_bootstrap_lib_jars

  rm -rf "$stage_dir/build/locker" "$transition_dir"
  run_ant no locker.lib
  copy_starr_classtag_classes "$seeded_lib"
  run_ant no locker.reflect locker.comp

  mkdir -p "$transition_dir/classes"
  cp -R "$stage_dir/build/locker/classes/library" "$transition_dir/classes/library"
  cp -R "$stage_dir/build/locker/classes/reflect" "$transition_dir/classes/reflect"
  cp -R "$stage_dir/build/locker/classes/compiler" "$transition_dir/classes/compiler"

  rm -rf "$stage_dir/build/locker" "$stage_dir/build/quick" "$stage_dir/build/pack" "$stage_dir/build/strap" "$stage_dir/build/palo"
  starr_lib="$transition_dir/classes/library"
  starr_reflect="$transition_dir/classes/reflect"
  active_starr_comp="$transition_dir/classes/compiler"
  run_ant no build
}

build_locker_pack_transition() {
  local pack_lib="$stage_dir/build/pack/lib"

  build_bootstrap_lib_jars

  rm -rf "$stage_dir/build/locker" "$stage_dir/build/quick" "$stage_dir/build/pack" "$stage_dir/build/strap" "$stage_dir/build/palo"
  run_ant no locker.done

  mkdir -p "$pack_lib"
  "$JAVA_HOME/bin/jar" cf "$pack_lib/scala-library.jar" -C "$stage_dir/build/locker/classes/library" .
  "$JAVA_HOME/bin/jar" cf "$pack_lib/scala-reflect.jar" -C "$stage_dir/build/locker/classes/reflect" .
  "$JAVA_HOME/bin/jar" cf "$pack_lib/scala-compiler.jar" -C "$stage_dir/build/locker/classes/compiler" .
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

if [[ "$mode" == "locker-pack" ]]; then
  run_ant no locker.clean
  build_deps
  build_locker_pack_transition
fi

if [[ "$mode" == "build" || "$mode" == "all" ]]; then
  run_ant no locker.clean clean
  build_deps
  if needs_classtag_string_transition; then
    build_classtag_string_transition
  elif needs_arraytag_transition; then
    build_arraytag_transition
  elif needs_anyval_class_transition || needs_compiler_first_transition; then
    build_compiler_first_transition
  else
    run_ant no build
  fi
fi

if [[ "$mode" == "test" || "$mode" == "all" ]]; then
  build_deps
  build_test_deps
  if grep -q 'name="test.suite.no-buildmanager"' "$stage_dir/build.xml"; then
    run_ant no test.t5293-map.java8
    run_ant icode test.icode.java8
    run_ant no test.suite.no-buildmanager test.continuations.suite
    run_ant java8boot test.repl-java8
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
