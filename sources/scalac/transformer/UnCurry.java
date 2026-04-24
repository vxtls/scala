/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.transformer;

import java.io.*;
import java.util.*;
import scalac.*;
import scalac.util.*;
import scalac.ast.*;
import scalac.symtab.*;
import scalac.ast.Tree.*;

/** - uncurry all symbol and tree types (@see UnCurryPhase)
 *  - for every curried parameter list:  (ps_1) ... (ps_n) ==> (ps_1, ..., ps_n)
 *  - for every curried application: f(args_1)...(args_n) ==> f(args_1, ..., args_n)
 *  - for every type application: f[Ts] ==> f[Ts]() unless followed by parameters
 *  - for every use of a parameterless function: f ==> f()  and  q.f ==> q.f()
 *  - for every def-parameter:  def x: T ==> x: () => T
 *  - for every use of a def-parameter: x ==> x.apply()
 *  - for every argument to a def parameter `def x: T':
 *      if argument is not a reference to a def parameter:
 *        convert argument `e' to (expansion of) `() => e'
 *  - for every argument list that corresponds to a repeated parameter
 *       (a_1, ..., a_n) => (Sequence(a_1, ..., a_n))
 *  - for every argument list that is an escaped sequence
 *       (a_1:_*) => (a_1)
 */
public class UnCurry extends OwnerTransformer
                     implements Modifiers {

    UnCurryPhase descr;
    Unit unit;

    public UnCurry(Global global, UnCurryPhase descr) {
        super(global);
	this.descr = descr;
    }

    public void apply(Unit unit) {
	this.unit = unit;
	super.apply(unit);
    }

    /** (ps_1) ... (ps_n) => (ps_1, ..., ps_n)
     */
    ValDef[][] uncurry(ValDef[][] params) {
	int n = 0;
	for (int i = 0; i < params.length; i++)
	    n = n + params[i].length;
	ValDef[] ps = new ValDef[n];
	int j = 0;
	for (int i = 0; i < params.length; i++) {
	    System.arraycopy(params[i], 0, ps, j, params[i].length);
	    j = j + params[i].length;
	}
	return new ValDef[][]{ps};
    }

    /** tree of non-method type T ==> same tree with method type ()T
     */
    Tree asMethod(Tree tree) {
	if (tree.type instanceof Type.MethodType) {
	    return tree;
	}
	return tree.setType(
	    Type.MethodType(Symbol.EMPTY_ARRAY, tree.type.widen()));
    }

    /** apply parameterless functions and def parameters
     */
    Tree applyDef(Tree tree1) {
	assert tree1.symbol() != null : tree1;
	Type symbolType = tree1.symbol().type();
	if (symbolType instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)symbolType;
            Symbol[] tparams = polyType.tparams;
            Type restp = polyType.result;
	    if (tparams.length == 0 && !(restp instanceof Type.MethodType)) {
		return gen.Apply(asMethod(tree1), new Tree[0]);
	    } else {
		return tree1;
	    }
	} else {
	    if (tree1.symbol().isDefParameter()) {
		tree1.type = global.definitions.FUNCTION_TYPE(
		    Type.EMPTY_ARRAY, tree1.type.widen());
		return gen.Apply(gen.Select(tree1, global.definitions.FUNCTION_APPLY(0)));
	    } else {
		return tree1;
	    }
	}
    }

    /** - uncurry all symbol and tree types (@see UnCurryPhase)
     *  - for every curried parameter list:  (ps_1) ... (ps_n) ==> (ps_1, ..., ps_n)
     *  - for every curried application: f(args_1)...(args_n) ==> f(args_1, ..., args_n)
     *  - for every type application: f[Ts] ==> f[Ts]() unless followed by parameters
     *  - for every use of a parameterless function: f ==> f()  and  q.f ==> q.f()
     *  - for every def-parameter:  def x: T ==> x: () => T
     *  - for every use of a def-parameter: x ==> x.apply()
     *  - for every argument to a def parameter `def x: T':
     *      if argument is not a reference to a def parameter:
     *        convert argument `e' to (expansion of) `() => e'
     *  - for every argument list that corresponds to a repeated parameter
     *       (a_1, ..., a_n) => (Sequence(a_1, ..., a_n))
     */
    public Tree transform(Tree tree) {
	//new scalac.ast.printer.TextTreePrinter().print("uncurry: ").print(tree).println().end();//DEBUG
	//uncurry type and symbol
	Type prevtype = tree.type;
	if (prevtype != null) {
	    if (prevtype instanceof Type.OverloadedType) {
		assert tree.symbol() != null;
		prevtype = tree.symbol().removeInheritedOverloaded(prevtype);
	    }
	    tree.type = descr.uncurry(prevtype);
	}
        if (tree instanceof ClassDef) {
            ClassDef classDef = (ClassDef)tree;
            AbsTypeDef[] tparams = classDef.tparams;
            ValDef[][] vparams = classDef.vparams;
            Tree tpe = classDef.tpe;
            Template impl = classDef.impl;
	    return copy.ClassDef(
		tree, tree.symbol(), tparams,
		uncurry(transform(vparams, tree.symbol())),
		tpe,
		transform(impl, tree.symbol()));
        }
	if (tree instanceof DefDef) {
            DefDef defDef = (DefDef)tree;
            AbsTypeDef[] tparams = defDef.tparams;
            ValDef[][] vparams = defDef.vparams;
            Tree tpe = defDef.tpe;
            Tree rhs = defDef.rhs;
	    Symbol sym = tree.symbol();
	    if (descr.isUnaccessedConstant(sym))
                return gen.mkUnitLit(tree.pos);
	    Tree rhs1 = transform(rhs, sym);
	    return copy.DefDef(
		tree, sym, tparams, uncurry(transform(vparams, sym)), tpe, rhs1);
        }
	if (tree instanceof ValDef) {
            ValDef valDef = (ValDef)tree;
            Tree tpe = valDef.tpe;
            Tree rhs = valDef.rhs;
	    Symbol sym = tree.symbol();
	    if (descr.isUnaccessedConstant(sym))
                return gen.mkUnitLit(tree.pos);
	    if (sym.isDefParameter()) {
		Type newtype = global.definitions.FUNCTION_TYPE(Type.EMPTY_ARRAY, tpe.type);
		Tree tpe1 = gen.mkType(tpe.pos, newtype);
                return copy.ValDef(tree, tpe1, rhs).setType(newtype);
	    } else {
		return super.transform(tree);
	    }
        }
	if (tree instanceof TypeApply) {
	    Tree tree1 = asMethod(super.transform(tree));
	    return gen.Apply(tree1, new Tree[0]);
        }
	if (tree instanceof Apply) {
            Apply apply = (Apply)tree;
            Tree fn = apply.fun;
            Tree[] args = apply.args;
	    // f(x)(y) ==> f(x, y)
	    // argument to parameterless function e => ( => e)
	    Type ftype = fn.type;
	    Tree fn1 = transform(fn);
            boolean myInArray =
                TreeInfo.methSymbol(fn1) == global.definitions.PREDEF_ARRAY();
            inArray = myInArray;
	    Tree[] args1 = transformArgs(tree.pos, args, ftype);
            if (myInArray) {
                if (fn1 instanceof Apply) {
                    Apply apply1 = (Apply)fn1;
                    if (apply1.fun instanceof TypeApply) {
                        TypeApply typeApply = (TypeApply)apply1.fun;
                        if (typeApply.fun instanceof Select) {
                            Select select = (Select)typeApply.fun;
                            return gen.mkBlock(args1[0].pos, select.qualifier, args1[0]);
                        }
                    }
                }
                throw Debug.abort("illegal Array application", fn1);
            }
	    if (TreeInfo.methSymbol(fn1) == global.definitions.ANY_MATCH &&
		!(args1[0] instanceof Tree.Visitor)) {
                Tree methPart = TreeInfo.methPart(fn1);
                if (methPart instanceof Select) {
                    Select select = (Select)methPart;
                    Tree qual = select.qualifier;
                    Name name = select.selector;
		    assert name == Names.match;
		    return gen.postfixApply(qual, args1[0], currentOwner);
                }
                throw new ApplicationError("illegal prefix for match: " + tree);

	    } else {
                if (fn1 instanceof Apply) {
                    Apply applied = (Apply)fn1;
                    Tree fn2 = applied.fun;
                    Tree[] args2 = applied.args;
		    Tree[] newargs = new Tree[args1.length + args2.length];
		    System.arraycopy(args2, 0, newargs, 0, args2.length);
		    System.arraycopy(args1, 0, newargs, args2.length, args1.length);
		    return copy.Apply(tree, fn2, newargs);
                }
		return copy.Apply(tree, fn1, args1);
	    }
        }
	if (tree instanceof Select) {
	    return applyDef(super.transform(tree));
        }
	if (tree instanceof Ident) {
            Name name = ((Ident)tree).name;
	    if (name == TypeNames.WILDCARD_STAR) {
		unit.error(tree.pos, " argument does not correspond to `*'-parameter");
		return tree;
	    } else if (tree.symbol() == global.definitions.PATTERN_WILDCARD) {
		return tree;
	    } else {
	        return applyDef(super.transform(tree));
	    }
	}
        if (tree instanceof CaseDef) {
            CaseDef caseDef = (CaseDef)tree;
            inPattern = true;
            Tree pat1 = transform(caseDef.pat);
            inPattern = false;
            Tree guard1 = transform(caseDef.guard);
            Tree body1 = transform(caseDef.body);
            return copy.CaseDef(tree, pat1, guard1, body1);
        }
	return super.transform(tree);
    }

    boolean inPattern = false;
    boolean inArray = false;

//    java.util.HashSet visited = new java.util.HashSet();//DEBUG

    /** Transform arguments `args' to method with type `methtype'.
     */
    private Tree[] transformArgs(int pos, Tree[] args, Type methtype) {
//	if (args.length != 0 && visited.contains(args)) {
//	    new scalac.ast.printer.TextTreePrinter().print("dup args: ").print(make.Block(pos, args)).println().end();//DEBUG
//	    assert false;
//	}
//	visited.add(args);//DEBUG

	if (methtype instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)methtype;
            Symbol[] params = methodType.vparams;
	    if (params.length > 0 &&
		(params[params.length-1].flags & REPEATED) != 0) {
		args = toSequence(pos, params, args);
	    }
	    Tree[] args1 = args;
	    for (int i = 0; i < args.length; i++) {
		Tree arg = args[i];
		Tree arg1 = transformArg(arg, params[i]);
		if (arg1 != arg && args1 == args) {
		    args1 = new Tree[args.length];
		    System.arraycopy(args, 0, args1, 0, i);
		}
		args1[i] = arg1;
	    }
	    return args1;
        }
	if (methtype instanceof Type.PolyType) {
            Type restp = ((Type.PolyType)methtype).result;
	    return transformArgs(pos, args, restp);
	}
	if (args.length == 0) return args; // could be arguments of nullary case pattern
	else throw new ApplicationError(methtype);
    }

    /** converts `a_1,...,a_n' to Seq(a_1,...,a_n)
     *  if a_n is an escaped sequence x:_*, takes care of escaping
     *
     *  precondition: params[params.length-1].flags & REPEATED != 0
     */
    private Tree[] toSequence( int pos, Symbol[] params, Tree[] args ) {
	Tree[] result = new Tree[params.length];
	for (int i = 0; i < params.length - 1; i++)
	    result[i] = args[i];
	assert (args.length != params.length
		|| !(args[params.length-1] instanceof Tree.Sequence)
		|| TreeInfo.isSequenceValued(args[params.length-1]));
 	if (args.length == params.length) {
            if (args[params.length-1] instanceof Typed) {
                Typed typed = (Typed)args[params.length-1];
                if (typed.tpe instanceof Ident &&
                    ((Ident)typed.tpe).name == TypeNames.WILDCARD_STAR) {
                    Tree arg = typed.expr;
		result[params.length-1] = arg;
		return result;
                }
            }
        }
	Tree[] args1 = args;
	if (params.length != 1) {
	    args1 = new Tree[args.length - (params.length - 1)];
	    System.arraycopy(args, params.length - 1, args1, 0, args1.length);
	}
        Type sequenceType = params[params.length-1].type();
        if (inPattern) {
            result[params.length-1] =
                make.Sequence(pos, args1).setType(sequenceType);
        } else if (inArray) {
            result[params.length-1] = gen.mkNewArray(
                pos, sequenceType.typeArgs()[0], args1, currentOwner);
        } else {
            result[params.length-1] = gen.mkNewList(
                pos, sequenceType.typeArgs()[0], args1);
        }
	return result;
    }

    /** for every argument to a def parameter `def x: T':
     *    if argument is not a reference to a def parameter:
     *      convert argument `e' to (expansion of) `() => e'
     */
    private Tree transformArg(Tree arg, Symbol formal) {
	if ((formal.flags & DEF) != 0) {
	    Symbol sym = arg.symbol();
	    if (sym != null && (sym.flags & DEF) != 0) {
		Tree arg1 = transform(arg);
		if (arg1 instanceof Apply) {
                    Apply apply = (Apply)arg1;
                    Tree fun = apply.fun;
                    Tree[] args1 = apply.args;
                    if (fun instanceof Select) {
                        Select select = (Select)fun;
                        Tree qual = select.qualifier;
                        Name name = select.selector;
		    assert name == Names.apply && args1.length == 0;
		    return qual;
                    }
                }
		System.err.println(arg1);//debug
		throw new ApplicationError();
	    }
	    return transform(
		gen.mkUnitFunction(arg, descr.uncurry(arg.type.widen()), currentOwner));
	} else {
	    return transform(arg);
	}
    }
}
