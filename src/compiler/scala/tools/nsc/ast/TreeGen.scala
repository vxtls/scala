/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$
package scala.tools.nsc.ast;

import scala.tools.nsc.util.Position;
import symtab.Flags._;

abstract class TreeGen {

  val global: Global;

  import global._;
  import definitions._;
  import posAssigner.atPos;

  /** Builds a reference to value whose type is given stable prefix.
   */
  def mkQualifier(tpe: Type): Tree =
    if (tpe == NoPrefix) {
      EmptyTree
    } else if (tpe.isInstanceOf[ThisType]) {
      val clazz = tpe.asInstanceOf[ThisType].sym;
      if (clazz.isRoot || clazz.isEmptyPackageClass) EmptyTree else This(clazz)
    } else if (tpe.isInstanceOf[SingleType]) {
      val stpe = tpe.asInstanceOf[SingleType];
      val pre = stpe.pre;
      val sym = stpe.sym;
      if (sym.isThisSkolem) {
        mkQualifier(ThisType(sym.deSkolemize))
      } else {
        val qual = mkStableRef(pre, sym);
        qual.tpe match {
          case MethodType(List(), restpe) =>
            Apply(qual, List()) setType restpe
          case _ =>
            qual
        }
      }
    } else if (tpe.isInstanceOf[TypeRef]) {
      val tref = tpe.asInstanceOf[TypeRef];
      val sym = tref.sym;
      assert(phase.erasedTypes);
      if (sym.isModuleClass && !sym.isRoot) {
        val qual = Select(mkQualifier(sym.owner.tpe), sym.sourceModule);
        qual.tpe match {
	  case MethodType(List(), restpe) =>
	    Apply(qual, List()) setType restpe
          case _ =>
            qual
        }
      } else This(sym)
    } else {
      throw new Error("unexpected qualifier type: " + tpe)
    }

  /** Builds a reference to given symbol with given stable prefix. */
  def mkRef(pre: Type, sym: Symbol): Tree  = {
    val qual = mkQualifier(pre);
    if (qual == EmptyTree) Ident(sym) else Select(qual, sym)
  }

  /** Builds a reference to given symbol. */
  def mkRef(sym: Symbol): Tree =
    if (sym.owner.isClass) mkRef(sym.owner.thisType, sym) else Ident(sym);

  /** Replaces tree type with a stable type if possible */
  def stabilize(tree: Tree): Tree = tree match {
    case Ident(_) =>
      if (tree.symbol.isStable) tree.setType(singleType(tree.symbol.owner.thisType, tree.symbol))
      else tree
    case Select(qual, _) =>
      if (tree.symbol.isStable && qual.tpe.isStable) tree.setType(singleType(qual.tpe, tree.symbol))
      else tree
    case _ =>
      tree
  }

  /** Cast `tree' to type `pt' */
  def cast(tree: Tree, pt: Type): Tree = {
    if (settings.debug.value) log("casting " + tree + ":" + tree.tpe + " to " + pt);
    assert(!tree.tpe.isInstanceOf[MethodType], tree);
    typer.typed {
      atPos(tree.pos) {
	Apply(TypeApply(Select(tree, Object_asInstanceOf), List(TypeTree(pt))), List())
      }
    }
  }

  /** Builds a reference with stable type to given symbol */
  def mkStableRef(pre: Type, sym: Symbol): Tree  = stabilize(mkRef(pre, sym));
  def mkStableRef(sym: Symbol): Tree  = stabilize(mkRef(sym));

  def This(sym: Symbol): Tree =
    global.This(sym.name) setSymbol sym setType sym.thisType;

  def Ident(sym: Symbol): Tree = {
    assert(sym.isTerm);
    global.Ident(sym.name) setSymbol sym setType sym.tpe;
  }

  def Select(qual: Tree, sym: Symbol): Tree =
    if (qual.symbol != null &&
        (qual.symbol.name.toTermName == nme.ROOT ||
         qual.symbol.name.toTermName == nme.EMPTY_PACKAGE_NAME)) {
      this.Ident(sym)
    } else {
      assert(sym.isTerm);
      val result = global.Select(qual, sym.name) setSymbol sym;
      if (qual.tpe != null) result setType qual.tpe.memberType(sym);
      result
    }

  /** Builds an instance test with given value and type. */
  def mkIsInstanceOf(value: Tree, tpe: Type, erased: Boolean): Tree = {
    val sym =
      if(erased)
        definitions.Any_isInstanceOfErased
      else
        definitions.Any_isInstanceOf;
    Apply(
      TypeApply(
        Select(value, sym),
        List(TypeTree(tpe))),
      List())
  }

  def mkIsInstanceOf(value: Tree, tpe: Type): Tree = {
    mkIsInstanceOf(value, tpe, global.phase.erasedTypes);
  }

  /** Builds a cast with given value and type. */
  def mkAsInstanceOf(value: Tree, tpe: Type, erased: Boolean): Tree = {
    val sym =
      if(erased)
        definitions.Any_asInstanceOfErased
      else
        definitions.Any_asInstanceOf;

    Apply(
      TypeApply(
        Select(value, sym),
        List(TypeTree(tpe))),
      List())
  }

  def mkAsInstanceOf(value: Tree, tpe: Type): Tree = {
    mkAsInstanceOf(value, tpe, global.phase.erasedTypes);
  }


  /** Builds a list with given head and tail. */
  def mkNewCons(head: Tree, tail: Tree):  Tree = {
    val elemType =
      if (tail.tpe != null && tail.tpe.widen.baseType(definitions.ListClass) != NoType)
        tail.tpe.widen.baseType(definitions.ListClass).typeArgs(0)
      else treeType(head);
    New(TypeTree(appliedType(definitions.ConsClass.typeConstructor, List(elemType))),
        List(List(head,tail)))
  }

  /** Builds a list with given head and tail. */
  def mkNil: Tree =
    mkRef(definitions.NilModule);

  /** Builds a pair */
  def mkNewPair(left: Tree, right: Tree) = {
    val pairType = appliedType(definitions.TupleClass(2).typeConstructor,
                               List(treeType(left), treeType(right)));
    New(TypeTree(pairType), List(List(left,right)))
  }

  private def treeType(tree: Tree): Type =
    if (tree == EmptyTree) definitions.AllClass.tpe
    else if (tree.tpe != null) tree.tpe
    else tree match {
      case Literal(Constant(_: Int)) =>
        definitions.IntClass.tpe
      case Literal(Constant(_: Boolean)) =>
        definitions.BooleanClass.tpe
      case Literal(Constant(_: Byte)) =>
        definitions.ByteClass.tpe
      case Literal(Constant(_: Short)) =>
        definitions.ShortClass.tpe
      case Literal(Constant(_: Char)) =>
        definitions.CharClass.tpe
      case Literal(Constant(_: Long)) =>
        definitions.LongClass.tpe
      case Literal(Constant(_: Float)) =>
        definitions.FloatClass.tpe
      case Literal(Constant(_: Double)) =>
        definitions.DoubleClass.tpe
      case Apply(TypeApply(Select(_, sym), List(tpt @ TypeTree())), List())
        if sym == definitions.Any_asInstanceOf || sym == definitions.Any_asInstanceOfErased =>
        tpt.tpe
      case _ =>
        definitions.AnyClass.tpe
    }

}
