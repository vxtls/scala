#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <stage-dir> <previous-scala-library.jar>" >&2
  exit 2
fi

stage_dir="$(cd "$1" && pwd -P)"
previous_library="$2"
out="$stage_dir/build/legacy-reflect-beans.jar"
tmp="$stage_dir/build/legacy-reflect-beans"

[[ -f "$previous_library" ]] || {
  echo "previous scala-library.jar is missing: $previous_library" >&2
  exit 1
}

rm -rf "$tmp"
mkdir -p "$tmp"

entries=(
  'scala/reflect/AnyValManifest$class.class'
  scala/reflect/AnyValManifest.class
  scala/reflect/BeanDescription.class
  scala/reflect/BeanDisplayName.class
  scala/reflect/BeanInfo.class
  scala/reflect/BeanInfoSkip.class
  scala/reflect/BeanProperty.class
  scala/reflect/BooleanBeanProperty.class
  'scala/reflect/ClassManifest$$anon$1.class'
  'scala/reflect/ClassManifest$$anon$2.class'
  'scala/reflect/ClassManifest$$anonfun$subargs$1.class'
  'scala/reflect/ClassManifest$.class'
  'scala/reflect/ClassManifest$class.class'
  scala/reflect/ClassManifest.class
  scala/reflect/ClassTypeManifest.class
  'scala/reflect/Code$.class'
  scala/reflect/Code.class
  'scala/reflect/Manifest$$anon$1.class'
  'scala/reflect/Manifest$$anon$10.class'
  'scala/reflect/Manifest$$anon$11.class'
  'scala/reflect/Manifest$$anon$12.class'
  'scala/reflect/Manifest$$anon$13.class'
  'scala/reflect/Manifest$$anon$14.class'
  'scala/reflect/Manifest$$anon$15$$anonfun$tpe$2.class'
  'scala/reflect/Manifest$$anon$15.class'
  'scala/reflect/Manifest$$anon$16.class'
  'scala/reflect/Manifest$$anon$17$$anonfun$tpe$3.class'
  'scala/reflect/Manifest$$anon$17.class'
  'scala/reflect/Manifest$$anon$2.class'
  'scala/reflect/Manifest$$anon$3.class'
  'scala/reflect/Manifest$$anon$4.class'
  'scala/reflect/Manifest$$anon$5.class'
  'scala/reflect/Manifest$$anon$6.class'
  'scala/reflect/Manifest$$anon$7.class'
  'scala/reflect/Manifest$$anon$8.class'
  'scala/reflect/Manifest$$anon$9.class'
  'scala/reflect/Manifest$.class'
  'scala/reflect/Manifest$ClassTypeManifest$$anonfun$tpe$1.class'
  'scala/reflect/Manifest$ClassTypeManifest.class'
  'scala/reflect/Manifest$SingletonTypeManifest.class'
  'scala/reflect/Manifest$class.class'
  scala/reflect/Manifest.class
  'scala/reflect/NoManifest$.class'
  scala/reflect/NoManifest.class
  scala/reflect/OptManifest.class
  'scala/reflect/ScalaBeanInfo$$anonfun$1$$anonfun$apply$1.class'
  'scala/reflect/ScalaBeanInfo$$anonfun$1.class'
  'scala/reflect/ScalaBeanInfo$$anonfun$2.class'
  scala/reflect/ScalaBeanInfo.class
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
