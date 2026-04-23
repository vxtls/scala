/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */
package scalac.typechecker;

import java.io.*;
import java.util.*;
import scalac.*;
import scalac.util.*;
import scalac.symtab.*;
import scalac.ast.*;
import scalac.ast.printer.*;
import scalac.ast.Tree.*;

/** A transformer for removing syntactic sugar. This transformer does
 *  not need any type or symbol-table information.
 *
 *  @author     Martin Odersky
 *  @version    2.0
 */
public class DeSugarize implements Kinds, Modifiers {

    /** the global environment
     */
    protected Global global;

    /** the tree factory
     */
    protected TreeFactory make;

    /** The copying factory
     */
    protected TreeCopier copy;

    /** the tree generator
     */
    protected TreeGen gen;

    /** the type inferencer
     */
    protected Infer infer;

    /** the name creator
     */
    protected final FreshNameCreator freshNameCreator;

    /** the constructor
     */
    /** the constructor
     */
    public DeSugarize(TreeFactory make, TreeCopier copy, TreeGen gen,
		      Infer infer, Global global) {
	this.global = global;
	this.make = make;
	this.copy = copy;
	this.gen = gen;
	this.infer = infer;
        this.freshNameCreator = global.freshNameCreator;
    }
    public DeSugarize(Analyzer analyzer, Global global) {
      this(analyzer.make, analyzer.copy, analyzer.gen, analyzer.infer, global);
    }

// Auxiliary definitions and functions -------------------------------------------

    /** introduce fresh variable of the form "ds$56"
     */
    Name getvar() {
        return freshNameCreator.newName("ds", '$');
    }

    Name setterName(Name name) {
	return name.append(Names._EQ);
    }

    Name parameterName(int i) {
	return Name.fromString("x$" + i);
    }

    Name tupleSelectorName(int i) {
	return Name.fromString("_" + i);
    }

    /** extract variables from a pattern
     */
    void getVariables(Tree tree, ArrayList vars) {
	if (tree instanceof Ident) {
	    Name name = ((Ident)tree).name;
	    if (name.isVariable() && name != Names.PATTERN_WILDCARD) vars.add(name);
	} else if (tree instanceof Typed) {
	    Tree expr = ((Typed)tree).expr;
	    getVariables(expr, vars);
	} else if (tree instanceof Apply) {
	    Tree fn = ((Apply)tree).fun;
	    Tree[] args = ((Apply)tree).args;
	    if (fn instanceof Apply) {
		getVariables(fn, vars);
	    }
	    for (int i = 0; i < args.length; i++)
		getVariables(args[i], vars);
	} else if (tree instanceof Sequence) {
	    Tree[] elems = ((Sequence)tree).trees;
	    for (int i = 0; i < elems.length; i++)
		getVariables(elems[i], vars);
	} else if (tree instanceof Literal) {
	    return;
	} else if (tree instanceof Bind) {
	    Name name = ((Bind)tree).name;
	    Tree t = ((Bind)tree).rhs;
	    if (name.isVariable() && name != Names.PATTERN_WILDCARD) vars.add(name);
	    getVariables( t, vars );
	} else if (tree instanceof Alternative) {
	    Tree[] ts = ((Alternative)tree).trees;
	    for (int i = 0; i < ts.length; i++)
		getVariables( ts[i], vars );
	} else {
	    throw new ApplicationError ("illegal pattern", tree);
	}
    }

// Transform functions -----------------------------------------------------

    /**  (T_1, ..., T_N) => T  ==>  scala.FunctionN[T_1, ..., T_N, +T]
     */
    public Tree FunType(Tree tree) {
	if (tree instanceof FunType) {
	    Tree[] argtpes = ((FunType)tree).argtpes;
	    Tree restpe = ((FunType)tree).restpe;
	    Tree[] types = new Tree[argtpes.length + 1];
	    System.arraycopy(argtpes, 0, types, 0, argtpes.length);
	    types[argtpes.length] = restpe;
	    return make.AppliedType(tree.pos,
		make.Select(tree.pos,
		    make.Ident(tree.pos, Names.scala),
		    Name.fromString("Function" + argtpes.length).toTypeName()),
		types);
	}
	throw new ApplicationError("function type expected", tree);
    }

    public Tree mkTuple(int pos, Tree[] trees) {
	if (trees.length == 0)
	    return gen.mkUnitLit(pos);
	else
	    return make.Apply(pos,
		make.Select(pos,
		    make.Ident(pos, Names.scala),
		    Name.fromString("Tuple" + trees.length)),
		trees);
    }

    /** Convert method to function type.
     */
    Type meth2fun(Type tp) {
	if (tp instanceof Type.MethodType) {
	    Symbol[] params = ((Type.MethodType)tp).vparams;
	    Type restype = ((Type.MethodType)tp).result;
	    return global.definitions.FUNCTION_TYPE(
		Symbol.type(params), meth2fun(restype));
	}
	return tp;
    }

    /** If `pt' is a matching function type insert missing parameters
     *  in `vparams' from it, and return result type,
     *  else return AnyType.
     */
    public Type preFunction(ValDef[] vparams, Type pt) {
	if (pt instanceof Type.TypeRef) {
	    Type[] ptargs = ((Type.TypeRef)pt).args;
	    Symbol psym = ((Type.TypeRef)pt).sym;
	    if (psym.fullName().startsWith(Names.scala_Function) &&
		ptargs.length == vparams.length + 1) {
		for (int i = 0; i < vparams.length; i++)
		    assignType(vparams[i], ptargs[i]);
		return ptargs[vparams.length];
	    }
	}
	return Type.AnyType;
    }
    //where
	void assignType(ValDef vparam, Type pt) {
	    if (vparam.tpe == Tree.Empty && infer.isFullyDefined(pt))
		vparam.tpe = gen.mkType(vparam.pos, pt);
	}

    public Tree isDefinedAtVisitor(Tree tree) {
	if (tree instanceof Visitor) {
	    CaseDef[] cases = ((Visitor)tree).cases;
	    CaseDef lastCase = cases[cases.length - 1];
	    if (lastCase.pat instanceof Ident &&
		((Ident)lastCase.pat).name.isVariable() &&
		lastCase.guard == Tree.Empty) {
		Name name = ((Ident)lastCase.pat).name;
		if (name.isVariable())
		    return make.Visitor(tree.pos,
			new CaseDef[]{
			    make.CaseDef(lastCase.pos,
				lastCase.pat.duplicate(),
				Tree.Empty,
				gen.mkBooleanLit(lastCase.body.pos, true))});
	    }
	    CaseDef[] cases1 = new CaseDef[cases.length + 1];
	    for (int i = 0; i < cases.length; i++) {
		CaseDef caseDef = cases[i];
		cases1[i] = (CaseDef) make.CaseDef(
		    caseDef.pos,
		    caseDef.pat.duplicate(),
		    caseDef.guard.duplicate(),
		    gen.mkBooleanLit(tree.pos, true));
		}
	    cases1[cases.length] = (CaseDef) make.CaseDef(
		tree.pos,
		gen.Ident(tree.pos, global.definitions.PATTERN_WILDCARD),
		Tree.Empty,
		gen.mkBooleanLit(tree.pos, false));
	    return make.Visitor(tree.pos, cases1);
	}
	throw new ApplicationError("visitor expected", tree);
    }

    /** match => this.match
     *  match[targs] => this.match[targs]
     *  IMPORTANT: tree is already attributed and attributes need to be preserved.
     */
    public Tree postMatch(Tree tree, Symbol currentclazz) {
	if (tree instanceof Ident) {
	    Name name = ((Ident)tree).name;
	    return
		make.Select(tree.pos,
		    gen.This(tree.pos, currentclazz),
		    name).setSymbol(tree.symbol()).setType(tree.type);
	}
	if (tree instanceof TypeApply) {
	    Tree fn = ((TypeApply)tree).fun;
	    Tree[] args = ((TypeApply)tree).args;
	    return copy.TypeApply(tree, postMatch(fn, currentclazz), args);
	}
	return tree;
    }

    /** { cases }   ==>   (x => x.match {cases})
     *  only called when match has to be added
     *  no type for parameter x
     */
    public Tree Visitor(Tree tree) {
        if (tree instanceof Visitor) {
	    Name x = getvar();
	    ValDef param = (ValDef) make.ValDef(
		tree.pos, PARAM, x, Tree.Empty, Tree.Empty);
	    Tree xuse = make.Ident(tree.pos, x);
	    // x.match {cases}
	    Tree body = make.Apply(tree.pos,
		make.Select(tree.pos, xuse, Names.match),
		new Tree[]{tree});
	    return make.Function(tree.pos, new ValDef[]{param}, body);
	}
	throw new ApplicationError("visitor expected", tree);
    }

    /** e = e'   ==>   e_=(e')
     */
    public Tree Assign(int pos, Tree lhs, Tree rhs) {
	Tree lhs1;
	if (lhs instanceof Ident) {
	    Name name = ((Ident)lhs).name;
	    lhs1 = make.Ident(lhs.pos, setterName(name));
	} else if (lhs instanceof Select) {
	    Tree qual = ((Select)lhs).qualifier;
	    Name name = ((Select)lhs).selector;
	    lhs1 = make.Select(lhs.pos, qual, setterName(name));
	} else {
	    throw new ApplicationError();
	}
	return make.Apply(pos, lhs1, new Tree[]{rhs});
    }

    /** e(args) = e'  ==>  e.update(args ; e')
     */
    public Tree Update(Tree tree) {
	if (tree instanceof Assign && ((Assign)tree).lhs instanceof Apply) {
	    Tree fn = ((Apply)((Assign)tree).lhs).fun;
	    Tree[] args = ((Apply)((Assign)tree).lhs).args;
	    Tree rhs = ((Assign)tree).rhs;
	    // e.update
	    Tree update = make.Select(fn.pos, fn, Names.update);
	    Tree[] args1 = new Tree[args.length + 1];
	    System.arraycopy(args, 0, args1, 0, args.length);
	    args1[args.length] = rhs;
	    return make.Apply(tree.pos, update, args1);
	}
	throw new ApplicationError();
    }

    /** make a set of trees share the same documentation comment as a
     * given tree (used for pattern and val definitions)
     */
    Tree[] shareComment(Tree[] trees, Tree tree) {
	String comment = (String) global.mapTreeComment.get(tree);
	if (comment != null)
	    for(int i = 0; i < trees.length; i++)
		global.mapTreeComment.put(trees[i], comment);
	return trees;
    }

    /** expand pattern definitions and variable definitions in templates.
     */
    public Tree[] Statements(Tree[] stats, boolean isLocal) {
	boolean change = false;
	for (int i = 0; i < stats.length && !change; i++) {
	    if (stats[i] instanceof PatDef) {
		change = true;
	    } else if (stats[i] instanceof ValDef) {
		int mods = ((ValDef)stats[i]).mods;
		change = !isLocal;
	    }
	}
	if (change) {
	    TreeList ts = new TreeList();
	    for (int i = 0; i < stats.length; i++) {
		if (stats[i] instanceof PatDef) {
		    ts.append(Statements(this.PatDef(stats[i]), isLocal));
		} else if (stats[i] instanceof ValDef) {
		    if (!isLocal) {
			ts.append(this.ValDef(stats[i]));
		    } else
			ts.append(stats[i]);
		} else {
		    ts.append(stats[i]);
		}
	    }
	    stats = ts.toArray();
	    //TextTreePrinter p = new TextTreePrinter();//debug
	    //p.print("desugarized:");//debug
	    //for (int i = 0; i < stats.length; i++) p.print(stats[i]).println();//debug
	    //p.end();//debug
	    return stats;
	} else {
	    return stats;
	}
    }

    /** expands pattern definitions
     *  in case pattern is a simple (typed) identifier:
     *  val x = e     ==>  val x = e
     *  val x: T = e  ==>  val x: T = e
     *
     *  in case there are no variables in pattern
     *  val p = e  ==>  e.match (case p => ())
     *
     *  in case there is exactly one variable in pattern
     *  val x_1 = e.match (case p => (x_1))
     *
     *  in case there are more variables in pattern
     *  val p = e  ==>  private synthetic val t$ = e.match (case p => (x_1, ..., x_N))
     *                  val x_1 = t$._1
     *                  ...
     *                  val x_N = t$._N
     *
     */
    public Tree[] PatDef(Tree tree) {
	if (tree instanceof PatDef &&
	    ((PatDef)tree).pat instanceof Ident) {
	    int mods = ((PatDef)tree).mods;
	    Name name = ((Ident)((PatDef)tree).pat).name;
	    Tree rhs = ((PatDef)tree).rhs;
	    // val x = e     ==>  val x = e
	    return shareComment(new Tree[]{
		make.ValDef(tree.pos, mods, name, Tree.Empty, rhs)}, tree);
	}
	if (tree instanceof PatDef &&
	    ((PatDef)tree).pat instanceof Typed &&
	    ((Typed)((PatDef)tree).pat).expr instanceof Ident) {
	    int mods = ((PatDef)tree).mods;
	    Name name = ((Ident)((Typed)((PatDef)tree).pat).expr).name;
	    Tree type = ((Typed)((PatDef)tree).pat).tpe;
	    Tree rhs = ((PatDef)tree).rhs;
	    // val x: T = e  ==> val x: T = e
	    return shareComment(new Tree[]{
		make.ValDef(tree.pos, mods, name, type, rhs)}, tree);
	}
	if (tree instanceof PatDef) {
	    int mods = ((PatDef)tree).mods;
	    Tree pat = ((PatDef)tree).pat;
	    Tree rhs = ((PatDef)tree).rhs;
	    int pos = tree.pos;
	    ArrayList varlist = new ArrayList();
	    getVariables(pat, varlist);
	    Name[] vars = new Name[varlist.size()];
	    varlist.toArray(vars);

	    // Tuple_N(x_1, ..., x_N)
	    Tree[] vtree = new Tree[vars.length];
	    for (int i = 0; i < vars.length; i++) {
		vtree[i] = make.Ident(pos, vars[i]);
	    }
	    Tree tuple = vars.length == 1 ? vtree[0] : mkTuple(tree.pos, vtree);

	    // e.match (case p => Tuple_N(x_1, ..., x_N))
	    CaseDef[] cases = {make.CaseDef(pos, pat, Tree.Empty, tuple)};
	    Tree match = make.Apply(pos,
				    make.Select(pos, rhs, Names.match),
				    new Tree[]{make.Visitor(pos, cases)});

	    if (vars.length == 0) {
		// e.match (case p => ())
		print(pat, "patdef", match);
		return new Tree[]{match};
	    } else if (vars.length == 1) {
		// val x_1 = e.match (case p => x_1)
		Tree valdef = make.ValDef(pos, mods, vars[0], Tree.Empty, match);
		print(pat, "patdef", valdef);
		return shareComment(new Tree[]{valdef}, tree);
	    } else {
		// t$
		Name var = getvar();

		// private synthetic val t$ = e.match (case p => (x_1, ..., x_N))
		Tree[] res = new Tree[vars.length + 1];
		res[0] = make.ValDef(pos, PRIVATE | SYNTHETIC, var,
				     Tree.Empty, match);
		for (int i = 0; i < vars.length; i ++) {
		    // val x_i = t$._i
		    res[i + 1] = make.ValDef(
			pos, mods, vars[i], Tree.Empty,
			make.Select(pos, make.Ident(pos, var), tupleSelectorName(i + 1)));
		}
		print(pat, "patdef", new Block(res, gen.mkUnitLit(pos)));//debug
		return shareComment(res, tree);
	    }
	}
	throw new ApplicationError("pattern definition expected", tree);
    }

    public Tree[] ValDef(Tree tree) {
	if (tree instanceof ValDef) {
	    int mods = ((ValDef)tree).mods;
	    Name name = ((ValDef)tree).name;
	    Tree tpe = ((ValDef)tree).tpe;
	    Tree rhs = ((ValDef)tree).rhs;
	    Name valname = Name.fromString(name + "$");
	    Tree valdef1 = copy.ValDef(
		tree, (mods & (DEFERRED | MUTABLE | CASEACCESSOR | MODUL)) | PRIVATE,
		valname, tpe, rhs).setType(null);
	    int mods1 = mods | ACCESSOR;
	    if ((mods1 & MUTABLE) == 0) mods1 |= STABLE;
	    Tree getter = make.DefDef(
		tree.pos, mods1, name,
		Tree.AbsTypeDef_EMPTY_ARRAY, Tree.ValDef_EMPTY_ARRAY_ARRAY,
		tpe.duplicate(),
		((mods & DEFERRED) != 0) ? Tree.Empty
		    : make.Ident(tree.pos, valname));
	    if ((mods1 & MUTABLE) == 0) {
		if ((mods1 & DEFERRED) != 0) return shareComment(new Tree[]{getter}, tree);
		else return shareComment(new Tree[]{valdef1, getter}, tree);
	    } else {
		Tree setter = make.DefDef(
		    tree.pos, mods1, setterName(name),
		    Tree.AbsTypeDef_EMPTY_ARRAY,
		    new ValDef[][]{{
			(ValDef) make.ValDef(
			    tree.pos, SYNTHETIC | PARAM, parameterName(0), tpe.duplicate(), Tree.Empty)}},
		    gen.mkType(tree.pos, global.definitions.UNIT_TYPE()),
		    ((mods1 & DEFERRED) != 0) ? Tree.Empty
		        : make.Assign(
			    tree.pos,
			    make.Ident(tree.pos, valname),
			    make.Ident(tree.pos, parameterName(0))));
		if ((mods1 & DEFERRED) != 0) return shareComment(new Tree[]{getter, setter}, tree);
		else return shareComment(new Tree[]{valdef1, getter, setter}, tree);
	    }
	}
	throw new ApplicationError();
    }

    /** Expand partial function applications of type `type'.
     *
     *  p.f(es_1)...(es_n)
     *     ==>  {
     *            private synthetic val eta$f    = p.f   // if p is not stable
     *            ...
     *            private synthetic val eta$e_i = e_i    // if e_i is not stable
     *             ...
     *
     *            (ps_1 => ... => ps_m => eta$f([es_1])...([es_m])(ps_1)...(ps_m))
     *          }
     *  tree is already attributed
     */
    public Tree etaExpand(Tree tree, Type type) {
	TreeList defs = new TreeList();
	Tree lambda =
	    toFunction(toApply(liftoutPrefix(tree, defs), type), type);
	Tree result = make.Block(tree.pos, defs.toArray(), lambda);
	print(tree, "eta", result);//debug
	return result;
    }

    private static String preName = "eta$";

    /** Append to `defs' value definitions for all non-stable subexpressions
     *  of the function application `tree'
     */
    public Tree liftoutPrefix(Tree tree, TreeList defs) {
	if (tree instanceof Ident) {
	    return tree;
	}
	if (tree instanceof Select) {
	    Tree qual = ((Select)tree).qualifier;
	    return copy.Select(tree, liftout(qual, defs)).setType(null);
	}
	if (tree instanceof TypeApply) {
	    Tree fn = ((TypeApply)tree).fun;
	    Tree[] args = ((TypeApply)tree).args;
	    return copy.TypeApply(tree, liftoutPrefix(fn, defs), args).setType(null);
	}
	if (tree instanceof Apply) {
	    Tree fn = ((Apply)tree).fun;
	    Tree[] args = ((Apply)tree).args;
	    return copy.Apply(tree, liftoutPrefix(fn, defs), liftout(args, defs))
		.setType(null);
	}
	throw new ApplicationError();
    }

    public Tree[] liftout(Tree[] trees, TreeList defs) {
	Tree[] trees1 = trees;
	for (int i = 0; i < trees.length; i++) {
	    Tree tree = trees[i];
	    Tree tree1 = liftout(tree, defs);
	    if (tree1 != tree && trees1 == trees) {
		trees1 = new Tree[trees.length];
		System.arraycopy(trees, 0, trees1, 0, trees.length);
	    }
	    trees1[i] = tree1;
	}
	return trees1;
    }

    public Tree liftout(Tree tree, TreeList defs) {
	if (!TreeInfo.isPureExpr(tree)) {
	    Name vname = Name.fromString(preName + defs.length());
	    defs.append(
		make.ValDef(
		    tree.pos, SYNTHETIC, vname, Tree.Empty, tree));
	    return make.Ident(tree.pos, vname);
	} else {
	    return tree;
	}
    }

    /**  in patterns x  => x @ _
     *   precondition:  name != '_'
     *   post: returns *unattributed* Bind tree
     */

      public Tree IdentPattern( Tree tree ) {
            if (tree instanceof Ident) {
                Name name = ((Ident)tree).name;
		if( name == Names.PATTERN_WILDCARD )
                    throw new ApplicationError("nothing to desugarize");
		return make.Bind( tree.pos,
				  name,
				  gen.Ident( tree.pos,
                                             global.definitions.PATTERN_WILDCARD ))
                    .setType( tree.type );
            }
	    throw new ApplicationError("ident expected");
      }

    /**  in patterns x:T  => x @ _ : T
     *   pre: t is a typed variable.
     *   post: returns *unattributed* Bind tree
     */

    public Tree TypedPattern( Tree.Typed t ) {
        if (t.expr instanceof Ident) {
            Name name = ((Ident)t.expr).name;
            Tree tpe = t.tpe;
            return make.Bind(t.pos,
                             name,
                             make.Typed(t.pos,
                                        gen.Ident( t.pos,
                                                   global.definitions.PATTERN_WILDCARD ),
                                        tpe));
        }
        throw new ApplicationError("unexpected Typed node");
    }


    /** f, (syms_1)...(syms_n)T    ==>    f(ps_1)...(ps_n)
     */
    Tree toApply(Tree tree, Type type) {
	if (type instanceof Type.MethodType) {
	    Symbol[] vparams = ((Type.MethodType)type).vparams;
	    Type restpe = ((Type.MethodType)type).result;
	    Tree res = make.Apply(tree.pos, tree, toIdents(vparams));
	    return toApply(res, restpe);
	}
	return tree;
    }

    /** e, (syms_1)...(syms_n)T    ==>    (ps_1 => ... => ps_n => e)
     */
    Tree toFunction(Tree tree, Type type) {
	if (type instanceof Type.MethodType) {
	    Symbol[] vparams = ((Type.MethodType)type).vparams;
	    Type restpe = ((Type.MethodType)type).result;
	    return //this.Function(
		make.Function(tree.pos, toVparams(vparams), toFunction(tree, restpe));
	    //restpe);
	}
	return tree;
    }

    /** Extract value parameters from type.
     */
    ValDef[] toVparams(Symbol[] symbols) {
	ValDef[] vpars = new ValDef[symbols.length];
	for (int i = 0; i < symbols.length; i++) {
	    vpars[i] = (ValDef)make.ValDef(
		symbols[i].pos, PARAM | SYNTHETIC, symbols[i].name,
		gen.mkType(symbols[i].pos, symbols[i].type()),
		Tree.Empty);
	}
	return vpars;
    }

    /** Extract value identifiers from method type.
     *  It is assumed that all symbols are term symbols  ==>  make.Ident().
     */
    Tree[] toIdents(Symbol[] symbols) {
	Tree[] idents = new Ident[symbols.length];
	for (int i = 0; i < symbols.length; i++) {
	    idents[i] = make.Ident(symbols[i].pos, symbols[i].name);
	}
	return idents;
    }

    /** Build value element definition name for case parameter.
     */
    public void addCaseElement(TreeList ts, ValDef vparam) {
	//vparam.symbol().initialize();
	ts.append(
	    make.ValDef(
		vparam.pos, CASEACCESSOR, vparam.name, Tree.Empty,
		make.Ident(vparam.pos, vparam.name)
		.setSymbol(vparam.symbol())));
    }

    /** add case constructor, value defintiions and access functions.
     */
    public Tree[] addCaseElements(Tree[] body, ValDef[] vparams) {
	TreeList stats = new TreeList();
	for (int i = 0; i < vparams.length; i++) {
	    addCaseElement(stats, vparams[i]);
	}
	stats.append(body);
	return stats.toArray();
    }

    //debug
    void print(Tree tree, String conv, Tree result) {
	if (global.log()) {
	    new TextTreePrinter()
		.print(tree).println()
		.print(" --" + conv + "--> ").println()
		.print(result).println().end();
	}
    }
}
