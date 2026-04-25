/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.checkers;

import scalac.util.*;
import scalac.ast.*;
import scalac.symtab.*;
import scalac.Global;
import scalac.util.Debug;
import scalac.ast.Tree.*;

/**
 * Check that the owner of symbols is set correctly.
 *
 * @author Michel Schinz
 * @version 1.0
 */

public class CheckOwners extends Checker {
    protected Symbol currentOwner;

    public CheckOwners(Global global) {
        super(global);
	currentOwner = global.definitions.ROOT_CLASS;
    }

    protected void traverse(Tree tree, Symbol owner) {
        Symbol prevOwner = currentOwner;
        currentOwner = owner;
        traverse(tree);
        currentOwner = prevOwner;
    }

    protected void traverse(Tree[] array, Symbol owner) {
        Symbol prevOwner = currentOwner;
        currentOwner = owner;
        traverse(array);
        currentOwner = prevOwner;
    }

    protected void traverse(Tree[][] array, Symbol owner) {
        Symbol prevOwner = currentOwner;
        currentOwner = owner;
        traverse(array);
        currentOwner = prevOwner;
    }

    protected void traverse(Template templ, Symbol owner) {
	Symbol prevOwner = currentOwner;
	if (owner.kind == Kinds.CLASS)
	    currentOwner = owner.primaryConstructor();
	traverse(templ.parents);
	currentOwner = owner;

        Symbol templSymbol = templ.symbol();
        Tree[] body = templ.body;
        for (int i = 0; i < body.length; ++i) {
            if (body[i] instanceof PackageDef
                || body[i] instanceof ClassDef
                || body[i] instanceof ModuleDef
                || body[i] instanceof DefDef
                || body[i] instanceof ValDef
                || body[i] instanceof AbsTypeDef
                || body[i] instanceof AliasTypeDef) {
                traverse(body[i], owner);
            } else {
                traverse(body[i], templSymbol);
            }
        }

	currentOwner = prevOwner;
    }

    protected void checkOwner(Tree tree, Symbol sym) {
        Symbol owner = sym.owner();
        verify(tree,
               owner == currentOwner,
               "owner",
               "incorrect owner for " + Debug.toString(sym) + ":\n"
               + "  found:    " + Debug.toString(owner) + "\n"
               + "  required: " + Debug.toString(currentOwner));
    }

    public void traverse(Tree tree) {
	if (tree instanceof PackageDef) {
            PackageDef packageDef = (PackageDef)tree;
            check(tree);
            traverse(packageDef.packaged);
            traverse(packageDef.impl, packageDef.packaged.symbol());
        } else if (tree instanceof ClassDef) {
            ClassDef classDef = (ClassDef)tree;
            check(tree);
            traverse(classDef.tparams, tree.symbol().primaryConstructor());
            traverse(classDef.vparams, tree.symbol().primaryConstructor());
	    traverse(classDef.tpe);
            traverse(classDef.impl, tree.symbol());
        } else if (tree instanceof ModuleDef) {
            ModuleDef moduleDef = (ModuleDef)tree;
            check(tree);
            traverse(moduleDef.tpe);
            traverse(moduleDef.impl, tree.symbol().moduleClass());
        } else if (tree instanceof DefDef) {
            DefDef defDef = (DefDef)tree;
            check(tree);
            traverse(defDef.tparams, tree.symbol());
            traverse(defDef.vparams, tree.symbol());
            traverse(defDef.tpe, tree.symbol());
            traverse(defDef.rhs, tree.symbol());
        } else if (tree instanceof ValDef) {
            ValDef valDef = (ValDef)tree;
            check(tree);
            traverse(valDef.tpe);
            traverse(valDef.rhs, tree.symbol());
        } else if (tree instanceof AbsTypeDef) {
            AbsTypeDef absTypeDef = (AbsTypeDef)tree;
            check(tree);
            traverse(absTypeDef.rhs, tree.symbol());
	    traverse(absTypeDef.lobound, tree.symbol());
        } else if (tree instanceof AliasTypeDef) {
            AliasTypeDef aliasTypeDef = (AliasTypeDef)tree;
            check(tree);
            traverse(aliasTypeDef.tparams, tree.symbol());
            traverse(aliasTypeDef.rhs, tree.symbol());
        } else {
	    super.traverse(tree);
        }
    }

    public void check(Tree tree) {
        if (tree.definesSymbol()) {
            Symbol sym = tree.symbol();
            if (sym != null && sym != Symbol.NONE) {
                checkOwner(tree, sym);
                if (sym.kind == Kinds.CLASS)
                    checkOwner(tree, sym.primaryConstructor());
            }
        }
    }
}
