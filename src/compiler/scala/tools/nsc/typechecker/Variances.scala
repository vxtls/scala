/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$
package scala.tools.nsc.typechecker;

import symtab.Flags._;

/** Variances form a lattice, 0 <= COVARIANT <= Variances, 0 <= CONTRAVARIANT <= VARIANCES
 */
trait Variances {

  val global: Global;
  import global._;

  /** Convert variance to string */
  private def varianceString(variance: int): String =
    if (variance == COVARIANT) "covariant"
    else if (variance == CONTRAVARIANT) "contravariant"
    else "invariant";

  /** Flip between covariant and contravariant */
  private def flip(v: int): int = {
    if (v == COVARIANT) CONTRAVARIANT;
    else if (v == CONTRAVARIANT) COVARIANT;
    else v
  }

  private def compose(v1: int, v2: int) =
    if (v1 == 0) 0
    else if (v1 == CONTRAVARIANT) flip(v2)
    else v2;

  /** Map everything below VARIANCES to 0 */
  private def cut(v: int): int =
    if (v == VARIANCES) v else 0;

  /** Compute variance of type parameter `tparam' in types of all symbols `sym'. */
  def varianceInSyms(syms: List[Symbol])(tparam: Symbol): int =
    (VARIANCES /: syms) ((v, sym) => v & varianceInSym(sym)(tparam));

  /** Compute variance of type parameter `tparam' in type of symbol `sym'. */
  def varianceInSym(sym: Symbol)(tparam: Symbol): int =
    if (sym.isAliasType) cut(varianceInType(sym.info)(tparam))
    else varianceInType(sym.info)(tparam);

  /** Compute variance of type parameter `tparam' in all types `tps'. */
  def varianceInTypes(tps: List[Type])(tparam: Symbol): int =
    (VARIANCES /: tps) ((v, tp) => v & varianceInType(tp)(tparam));

  /** Compute variance of type parameter `tparam' in all type arguments
   *  `tps' which correspond to formal type parameters `tparams1'. */
  def varianceInArgs(tps: List[Type], tparams1: List[Symbol])(tparam: Symbol): int = {
    var v: int = VARIANCES;
    for (val Pair(tp, tparam1) <- tps zip tparams1) {
      val v1 = varianceInType(tp)(tparam);
      v = v & (if (tparam1.isCovariant) v1
	       else if (tparam1.isContravariant) flip(v1)
	       else cut(v1))
    }
    v
  }

  /** Compute variance of type parameter `tparam' in type `tp'. */
  def varianceInType(tp: Type)(tparam: Symbol): int =
    if (tp == ErrorType || tp == WildcardType || tp == NoType || tp == NoPrefix ||
        tp.isInstanceOf[ThisType] || tp.isInstanceOf[ConstantType]) {
      VARIANCES
    } else if (tp.isInstanceOf[SingleType]) {
      cut(varianceInType(tp.asInstanceOf[SingleType].pre)(tparam))
    } else if (tp.isInstanceOf[TypeRef]) {
      val tref = tp.asInstanceOf[TypeRef];
      if (tref.sym == tparam) COVARIANT
      else varianceInType(tref.pre)(tparam) & varianceInArgs(tref.args, tref.sym.typeParams)(tparam)
    } else if (tp.isInstanceOf[TypeBounds]) {
      val bnds = tp.asInstanceOf[TypeBounds];
      flip(varianceInType(bnds.lo)(tparam)) & varianceInType(bnds.hi)(tparam)
    } else if (tp.isInstanceOf[RefinedType]) {
      val rt = tp.asInstanceOf[RefinedType];
      varianceInTypes(rt.parents)(tparam) & varianceInSyms(rt.decls.toList)(tparam)
    } else if (tp.isInstanceOf[MethodType]) {
      val mt = tp.asInstanceOf[MethodType];
      flip(varianceInTypes(mt.paramTypes)(tparam)) & varianceInType(mt.resultType)(tparam)
    } else if (tp.isInstanceOf[PolyType]) {
      val pt = tp.asInstanceOf[PolyType];
      flip(varianceInSyms(pt.typeParams)(tparam)) & varianceInType(pt.resultType)(tparam)
    } else {
      throw new Error("unexpected type in variance computation: " + tp)
    }
}
