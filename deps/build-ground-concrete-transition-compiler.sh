#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 5 ]]; then
  echo "usage: $0 <scala-stage-dir> <starr-library.jar> <starr-compiler.jar> <java-bootclasspath> <version-number>" >&2
  exit 2
fi

stage_dir="$(cd "$1" && pwd -P)"
starr_lib="$2"
starr_comp="$3"
java_bootclasspath="$4"
version_number="$5"

java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
work_dir="$stage_dir/build/transition-ground-concrete"
src_dir="$work_dir/src"
lib_classes="$work_dir/classes/library"
comp_classes="$work_dir/classes/compiler"

for required in "$starr_lib" "$starr_comp" "$stage_dir/build/libs/fjbg.jar" "$stage_dir/build/libs/forkjoin.jar" "$stage_dir/build/libs/jline.jar" "$stage_dir/lib/extra/msil-source.jar"; do
  [[ -e "$required" ]] || {
    echo "required transition input is missing: $required" >&2
    exit 1
  }
done

ant_home="${ANT_HOME:-}"
if [[ -z "$ant_home" && -n "$(command -v ant 2>/dev/null)" ]]; then
  ant_home="$(cd "$(dirname "$(command -v ant)")/.." && pwd -P)"
fi
ant_jar="$ant_home/lib/ant.jar"
[[ -f "$ant_jar" ]] || {
  echo "ant.jar is required to build the transition compiler: $ant_jar" >&2
  exit 1
}

rm -rf "$work_dir"
mkdir -p "$src_dir/scala/reflect/api" "$src_dir/scala/reflect/makro/internal" "$lib_classes" "$comp_classes"

find "$stage_dir/src/library" -name '*.java' | sort > "$work_dir/library-java.list"
"$javac_bin" -source 1.5 -target 1.5 -XDignore.symbol.file -d "$lib_classes" @"$work_dir/library-java.list"

python3 - "$stage_dir" "$src_dir" <<'PY'
from pathlib import Path
import sys

stage = Path(sys.argv[1])
src_dir = Path(sys.argv[2])

typetags = (stage / "src/library/scala/reflect/api/TypeTags.scala").read_text()
needle = """    def unapply[T](ttag: TypeTag[T]): Option[Type] = if (ttag.isConcrete) Some(ttag.tpe) else None

    implicit def toClassTag[T](ttag: rm.ConcreteTypeTag[T]): ClassTag[T] = ClassTag[T](rm.typeToClass(ttag.tpe.erasure))
"""
replacement = """    def unapply[T](ttag: TypeTag[T]): Option[Type] = if (ttag.isConcrete) Some(ttag.tpe) else None

    implicit def materializeForBootstrap[T]: ConcreteTypeTag[T] = Any.asInstanceOf[ConcreteTypeTag[T]]

    implicit def toClassTag[T](ttag: rm.ConcreteTypeTag[T]): ClassTag[T] = ClassTag[T](rm.typeToClass(ttag.tpe.erasure))
"""
if needle not in typetags:
    raise SystemExit("ConcreteTypeTag insertion point not found")
typetags = typetags.replace(needle, replacement)

ground_alias = """

  @annotation.implicitNotFound(msg = "No GroundTypeTag available for ${T}")
  class GroundTypeTag[T](tpe: Type) extends ConcreteTypeTag[T](tpe) {
    override def productPrefix = "GroundTypeTag"
  }

  object GroundTypeTag {
    val Byte    : GroundTypeTag[scala.Byte]       = new GroundTypeTag[scala.Byte](ByteTpe) { private def readResolve() = GroundTypeTag.Byte }
    val Short   : GroundTypeTag[scala.Short]      = new GroundTypeTag[scala.Short](ShortTpe) { private def readResolve() = GroundTypeTag.Short }
    val Char    : GroundTypeTag[scala.Char]       = new GroundTypeTag[scala.Char](CharTpe) { private def readResolve() = GroundTypeTag.Char }
    val Int     : GroundTypeTag[scala.Int]        = new GroundTypeTag[scala.Int](IntTpe) { private def readResolve() = GroundTypeTag.Int }
    val Long    : GroundTypeTag[scala.Long]       = new GroundTypeTag[scala.Long](LongTpe) { private def readResolve() = GroundTypeTag.Long }
    val Float   : GroundTypeTag[scala.Float]      = new GroundTypeTag[scala.Float](FloatTpe) { private def readResolve() = GroundTypeTag.Float }
    val Double  : GroundTypeTag[scala.Double]     = new GroundTypeTag[scala.Double](DoubleTpe) { private def readResolve() = GroundTypeTag.Double }
    val Boolean : GroundTypeTag[scala.Boolean]    = new GroundTypeTag[scala.Boolean](BooleanTpe) { private def readResolve() = GroundTypeTag.Boolean }
    val Unit    : GroundTypeTag[scala.Unit]       = new GroundTypeTag[scala.Unit](UnitTpe) { private def readResolve() = GroundTypeTag.Unit }
    val Any     : GroundTypeTag[scala.Any]        = new GroundTypeTag[scala.Any](AnyTpe) { private def readResolve() = GroundTypeTag.Any }
    val Object  : GroundTypeTag[java.lang.Object] = new GroundTypeTag[java.lang.Object](ObjectTpe) { private def readResolve() = GroundTypeTag.Object }
    val AnyVal  : GroundTypeTag[scala.AnyVal]     = new GroundTypeTag[scala.AnyVal](AnyValTpe) { private def readResolve() = GroundTypeTag.AnyVal }
    val AnyRef  : GroundTypeTag[scala.AnyRef]     = new GroundTypeTag[scala.AnyRef](AnyRefTpe) { private def readResolve() = GroundTypeTag.AnyRef }
    val Nothing : GroundTypeTag[scala.Nothing]    = new GroundTypeTag[scala.Nothing](NothingTpe) { private def readResolve() = GroundTypeTag.Nothing }
    val Null    : GroundTypeTag[scala.Null]       = new GroundTypeTag[scala.Null](NullTpe) { private def readResolve() = GroundTypeTag.Null }
    val String  : GroundTypeTag[java.lang.String] = new GroundTypeTag[java.lang.String](StringTpe) { private def readResolve() = GroundTypeTag.String }

    def apply[T](tpe: Type): GroundTypeTag[T] = new GroundTypeTag[T](tpe) {}
    def unapply[T](ttag: TypeTag[T]): Option[Type] = if (ttag.isConcrete) Some(ttag.tpe) else None
    implicit def toClassTag[T](ttag: rm.GroundTypeTag[T]): ClassTag[T] = ClassTag[T](rm.typeToClass(ttag.tpe.erasure))
  }
"""
idx = typetags.rfind("\n}")
if idx < 0:
    raise SystemExit("TypeTags closing brace not found")
(src_dir / "scala/reflect/api/TypeTags.scala").write_text(typetags[:idx] + ground_alias + typetags[idx:])

type_tag_impl = (stage / "src/library/scala/reflect/makro/internal/typeTagImpl.scala").read_text()
needle = """  /** This method is required by the compiler and <b>should not be used in client code</b>. */
  def materializeConcreteTypeTag[T](u: Universe): u.ConcreteTypeTag[T] = macro materializeConcreteTypeTag_impl[T]
"""
ground_materializer = """  /** This method is required by the bootstrap compiler and <b>should not be used in client code</b>. */
  def materializeGroundTypeTag[T](u: Universe): u.GroundTypeTag[T] = macro materializeGroundTypeTag_impl[T]

  /** This method is required by the bootstrap compiler and <b>should not be used in client code</b>. */
  def materializeGroundTypeTag_impl[T: c.TypeTag](c: Context)(u: c.Expr[Universe]): c.Expr[u.value.GroundTypeTag[T]] =
    c.Expr[Nothing](c.materializeTypeTag(u.tree, implicitly[c.TypeTag[T]].tpe, requireConcreteTypeTag = true))(c.TypeTag.Nothing)

"""
if needle not in type_tag_impl:
    raise SystemExit("typeTagImpl insertion point not found")
(src_dir / "scala/reflect/makro/internal/typeTagImpl.scala").write_text(type_tag_impl.replace(needle, ground_materializer + needle))
PY

{
  echo "$src_dir/scala/reflect/api/TypeTags.scala"
  echo "$src_dir/scala/reflect/makro/internal/typeTagImpl.scala"
  find "$stage_dir/src/library" -name '*.scala' \
    ! -path '*/scala/reflect/api/TypeTags.scala' \
    ! -path '*/scala/reflect/makro/internal/typeTagImpl.scala' | sort
} > "$work_dir/library-scala.list"

"$java_bin" -Xmx1536M -cp "$starr_comp:$starr_lib" scala.tools.nsc.Main \
  -Xmacros \
  -javabootclasspath "$java_bootclasspath" \
  -classpath "$lib_classes:$starr_lib:$stage_dir/build/libs/forkjoin.jar:$stage_dir/lib/extra/jansi-1.4.jar:$stage_dir/lib/extra/typesafe-config-0.3.0.jar" \
  -d "$lib_classes" \
  @"$work_dir/library-scala.list"

find "$stage_dir/src/compiler" -name '*.scala' | sort > "$work_dir/compiler-scala.list"

"$java_bin" -Xmx1536M -cp "$starr_comp:$starr_lib" scala.tools.nsc.Main \
  -Xmacros \
  -javabootclasspath "$java_bootclasspath" \
  -classpath "$lib_classes:$comp_classes:$stage_dir/build/libs/fjbg.jar:$stage_dir/build/libs/forkjoin.jar:$stage_dir/build/libs/jline.jar:$stage_dir/lib/extra/jansi-1.4.jar:$stage_dir/lib/extra/typesafe-config-0.3.0.jar:$stage_dir/lib/extra/msil-source.jar:$ant_jar" \
  -sourcepath "$stage_dir/src/compiler" \
  -d "$comp_classes" \
  @"$work_dir/compiler-scala.list"

while IFS= read -r resource; do
  rel="${resource#$stage_dir/src/compiler/}"
  mkdir -p "$comp_classes/$(dirname "$rel")"
  cp "$resource" "$comp_classes/$rel"
done < <(find "$stage_dir/src/compiler" -type f ! -name '*.scala' | sort)

echo "built $work_dir/classes for $version_number"
