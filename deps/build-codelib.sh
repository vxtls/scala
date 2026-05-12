#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <scala-stage-dir> <java-bootclasspath>" >&2
  exit 2
fi

stage_dir="$1"
java_bootclasspath="$2"
desired="$stage_dir/test/files/codelib/code.jar.desired.sha1"
out_dir="$stage_dir/test/files/codelib"
out_jar="$out_dir/code.jar"
work_dir="$stage_dir/build/source-deps/codelib"
src_file="$work_dir/src/scala/reflect/Code.scala"
classes_dir="$work_dir/classes"
scalac_bin="$stage_dir/build/pack/bin/scalac"
scala_lib="$stage_dir/build/pack/lib/scala-library.jar"

[[ -f "$desired" ]] || exit 0
[[ -x "$scalac_bin" ]] || {
  echo "scala pack compiler does not exist: $scalac_bin" >&2
  exit 1
}
[[ -f "$scala_lib" ]] || {
  echo "scala pack library does not exist: $scala_lib" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$(dirname "$src_file")" "$classes_dir" "$out_dir"

cat > "$src_file" <<'EOF'
package scala.reflect

class Code(val tree: scala.reflect.mirror.Tree) {
  override def toString = "Code(tree = "+tree+")"
}

object Code {
  def macro lift[A](tree: A): scala.reflect.Code = (A, tree) match {
    case (tpt: TypeTree, tree: Tree) =>
      import _context._
      val codeTpt = Select(Select(Ident(newTermName("scala")), newTermName("reflect")), newTypeName("Code"))
      Apply(Select(New(codeTpt), newTermName("<init>")), List(reify(tree)))
  }
}
EOF

"$scalac_bin" \
  -Xmacros \
  -javabootclasspath "$java_bootclasspath" \
  -classpath "$scala_lib" \
  -d "$classes_dir" \
  "$src_file"

rm -f "$out_jar"
(cd "$classes_dir" && jar cf "$out_jar" .)

echo "built $out_jar"
