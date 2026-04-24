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


/** A default transformer class which also maintains owner information
 *
 *  @author     Martin Odersky
 *  @version    1.0
 */
public class OwnerTransformer extends Transformer {

    protected Symbol currentOwner;

    public OwnerTransformer(Global global) {
        super(global);
    }

    public void apply(Unit unit) {
	currentOwner = global.definitions.ROOT_CLASS;
        unit.body = transform(unit.body);
    }

    public Tree transform(Tree tree, Symbol owner) {
	Symbol prevOwner = currentOwner;
	currentOwner = owner;
	Tree tree1 = transform(tree);
	currentOwner = prevOwner;
	return tree1;
    }

    public AbsTypeDef[] transform(AbsTypeDef[] params, Symbol owner) {
	Symbol prevOwner = currentOwner;
	currentOwner = owner;
	AbsTypeDef[] res = transform(params);
	currentOwner = prevOwner;
	return res;
    }

    public ValDef[][] transform(ValDef[][] params, Symbol owner) {
	Symbol prevOwner = currentOwner;
	currentOwner = owner;
	ValDef[][] res = transform(params);
	currentOwner = prevOwner;
	return res;
    }

    public Template transform(Template templ, Symbol owner) {
	Symbol prevOwner = currentOwner;
	if (owner.kind == Kinds.CLASS)
	    currentOwner = owner.primaryConstructor();
	Tree[] parents1 = transform(templ.parents);
	currentOwner = owner;
	Tree[] body1 = transformTemplateStats(templ.body, templ.symbol());
	currentOwner = prevOwner;
	return copy.Template(templ, parents1, body1);
    }

    public Tree[] transformTemplateStats(Tree[] ts, Symbol tsym) {
	Tree[] ts1 = ts;
	for (int i = 0; i < ts.length; i++) {
            Tree t = transformTemplateStat(ts[i], tsym);
            if (t != ts[i] && ts1 == ts) {
                ts1 = new Tree[ts.length];
                System.arraycopy(ts, 0, ts1, 0, i);
	    }
	    ts1[i] = t;
        }
        return ts1;
    }

    public Tree transformTemplateStat(Tree stat, Symbol tsym) {
	return transform(stat, tsym);
    }

    public Tree transform(Tree tree) {
	if (tree instanceof PackageDef) {
            PackageDef packageDef = (PackageDef)tree;
            Tree packaged = packageDef.packaged;
            Template impl = packageDef.impl;
	    return copy.PackageDef(
		tree,
                transform(packaged),
                transform(impl, packaged.symbol()));
        }
	if (tree instanceof ClassDef) {
            ClassDef classDef = (ClassDef)tree;
            AbsTypeDef[] tparams = classDef.tparams;
            ValDef[][] vparams = classDef.vparams;
            Tree tpe = classDef.tpe;
            Template impl = classDef.impl;
            Symbol symbol = tree.symbol();
	    return copy.ClassDef(
		tree, symbol,
		transform(tparams, symbol.primaryConstructor()),
		transform(vparams, symbol.primaryConstructor()),
		transform(tpe, symbol),
		transform(impl, symbol));
        }
	if (tree instanceof ModuleDef) {
            ModuleDef moduleDef = (ModuleDef)tree;
            Tree tpe = moduleDef.tpe;
            Template impl = moduleDef.impl;
            Symbol symbol = tree.symbol();
	    return copy.ModuleDef(
		tree, symbol,
                transform(tpe, symbol),
		transform(impl, symbol.moduleClass()));
        }
	if (tree instanceof DefDef) {
            DefDef defDef = (DefDef)tree;
            AbsTypeDef[] tparams = defDef.tparams;
            ValDef[][] vparams = defDef.vparams;
            Tree tpe = defDef.tpe;
            Tree rhs = defDef.rhs;
            Symbol symbol = tree.symbol();
	    return copy.DefDef(
		tree, symbol,
		transform(tparams, symbol),
		transform(vparams, symbol),
		transform(tpe, symbol),
		transform(rhs, symbol));
        }
	if (tree instanceof ValDef) {
            ValDef valDef = (ValDef)tree;
            Tree tpe = valDef.tpe;
            Tree rhs = valDef.rhs;
            Symbol symbol = tree.symbol();
	    return copy.ValDef(
		tree, symbol,
                transform(tpe),
		transform(rhs, symbol));
        }
	if (tree instanceof AbsTypeDef) {
            AbsTypeDef absTypeDef = (AbsTypeDef)tree;
            Tree rhs = absTypeDef.rhs;
            Tree lobound = absTypeDef.lobound;
	    Symbol symbol = tree.symbol();
	    return copy.AbsTypeDef(
		tree, symbol,
		transform(rhs, symbol),
		transform(lobound, symbol));
        }
	if (tree instanceof AliasTypeDef) {
            AliasTypeDef aliasTypeDef = (AliasTypeDef)tree;
            AbsTypeDef[] tparams = aliasTypeDef.tparams;
            Tree rhs = aliasTypeDef.rhs;
	    Symbol symbol = tree.symbol();
	    return copy.AliasTypeDef(
		tree, symbol,
		transform(tparams, symbol),
		transform(rhs, symbol));
	}
	return super.transform(tree);
    }
}
