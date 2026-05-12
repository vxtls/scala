#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <stage-dir> <previous-scala-library.jar>" >&2
  exit 2
fi

stage_dir="$(cd "$1" && pwd -P)"
previous_library="$2"
out="$stage_dir/build/legacy-beans-meta.jar"
tmp="$stage_dir/build/legacy-beans-meta"

[[ -f "$previous_library" ]] || {
  echo "previous scala-library.jar is missing: $previous_library" >&2
  exit 1
}

rm -rf "$tmp"
mkdir -p "$tmp"

entries=(
  scala/beans/meta/beanGetter.class
  scala/beans/meta/beanSetter.class
  scala/beans/meta/field.class
  scala/beans/meta/getter.class
  'scala/beans/meta/package$.class'
  scala/beans/meta/package.class
  scala/beans/meta/param.class
  scala/beans/meta/setter.class
)

available=()
for entry in "${entries[@]}"; do
  if jar tf "$previous_library" | grep -Fxq "$entry"; then
    available+=("$entry")
  fi
done

if [[ "${#available[@]}" -gt 0 ]]; then
  (
    cd "$tmp"
    jar xf "$previous_library" "${available[@]}"
  )
fi

jar cf "$out" -C "$tmp" .
echo "built $out"
