#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <scala-stage-dir> <base-java8-stubs-jar>" >&2
  exit 2
fi

stage_dir="$1"
base_stubs_jar="$2"
work_dir="$stage_dir/build/source-deps/java8-filtered-stubs"
out_jar="$stage_dir/build/java8-filtered-stubs.jar"

[[ -f "$base_stubs_jar" ]] || {
  echo "base Java 8 stubs jar does not exist: $base_stubs_jar" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$work_dir"

(cd "$work_dir" && jar xf "$base_stubs_jar")

# Keep only the Java 8 signatures that the old compiler cannot read from rt.jar.
# Broad AWT/Swing stubs hide real JDK APIs and break scala-swing compilation.
if [[ -d "$work_dir/javax/swing" ]]; then
  find "$work_dir/javax/swing" -type f ! -name 'JComponent.class' -delete
fi
rm -f \
  "$work_dir/java/nio/Buffer.class" \
  "$work_dir/java/awt/AWTEvent.class" \
  "$work_dir/java/awt/BorderLayout.class" \
  "$work_dir/java/awt/Component.class" \
  "$work_dir/java/awt/Container.class" \
  "$work_dir/java/awt/EventQueue.class" \
  "$work_dir/java/awt/Toolkit.class" \
  "$work_dir/java/awt/Window.class"
rm -rf "$work_dir/java/awt/event"

jar cf "$out_jar" -C "$work_dir" .

echo "built $out_jar"
