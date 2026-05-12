#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <scala-stage-dir>" >&2
  exit 2
fi

stage_dir="$1"
jvm_dir="$stage_dir/test/files/jvm"
support_dir="$stage_dir/test/support/annotations"
lib_dir="$stage_dir/test/files/lib"
work_dir="$stage_dir/build/source-deps/partest-jvm"

[[ -n "${JAVA_HOME:-}" ]] || {
  echo "JAVA_HOME must point at a JDK 8 installation" >&2
  exit 1
}

javac_bin="$JAVA_HOME/bin/javac"
jar_bin="$JAVA_HOME/bin/jar"
rm -rf "$work_dir"
mkdir -p "$work_dir" "$lib_dir"

build_jar() {
  local name="$1"
  shift
  local classes="$work_dir/$name/classes"
  rm -rf "$classes"
  mkdir -p "$classes"
  "$javac_bin" -source 6 -target 6 -d "$classes" "$@"
  "$jar_bin" cf "$lib_dir/$name.jar" -C "$classes" .
  echo "built $lib_dir/$name.jar"
}

build_jar annotations \
  "$support_dir/SourceAnnotation.java" \
  "$support_dir/NestedAnnotations.java"

build_jar enums \
  "$support_dir/OuterEnum.java"

build_jar nest \
  "$jvm_dir/nest.java"

build_jar methvsfield \
  "$jvm_dir/methvsfield.java"

generic_src="$work_dir/genericNest/src/nestpkg/OuterTParams.java"
mkdir -p "$(dirname "$generic_src")"
{
  echo "package nestpkg;"
  sed -e '/^package /d' -e 's/class InnerClass/public class InnerClass/' "$support_dir/OuterTParams.java"
} > "$generic_src"
build_jar genericNest "$generic_src"

native_out="$jvm_dir/libnatives"
case "$(uname -s)" in
  Darwin)
    cc -dynamiclib \
      -I"$JAVA_HOME/include" \
      -I"$JAVA_HOME/include/darwin" \
      -o "$native_out.dylib" \
      "$jvm_dir/natives.c"
    cp "$native_out.dylib" "$native_out.jnilib"
    echo "built $native_out.dylib"
    ;;
  Linux)
    cc -shared -fPIC \
      -I"$JAVA_HOME/include" \
      -I"$JAVA_HOME/include/linux" \
      -o "$native_out.so" \
      "$jvm_dir/natives.c"
    echo "built $native_out.so"
    ;;
  *)
    echo "native partest dependency is not configured for $(uname -s)" >&2
    exit 1
    ;;
esac
