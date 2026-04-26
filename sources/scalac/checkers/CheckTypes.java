/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.checkers;

import scalac.ast.Tree;
import scalac.util.Name;
import scalac.symtab.Type;
import scalac.symtab.Symbol;
import scalac.symtab.Kinds;
import scalac.Global;
import scalac.util.Debug;

/**
 * Check the following properties:
 *   1. all tree nodes have a type,
 *   2. TypeRefs supply the correct number of arguments,
 *   3. type terms are represented as TypeTerm nodes.
 *
 * @author Michel Schinz
 * @version 1.0
 */

public class CheckTypes extends Checker {
    public CheckTypes(Global global) { super(global); }

    public void checkIsTypeTerm(Tree tree, Tree tpe) {
        verify(tree,
               tpe == Tree.Empty || tpe instanceof Tree.TypeTerm,
               "no syntactic types",
               "`tpe' part of tree is not an instance of TypeTerm");
    }

    public void check(Tree tree) {
	verify(tree, tree.type != null, "non-null type", "type of tree is not null");

        if (tree.type != null) {
            if (tree.type instanceof Type.TypeRef) {
                Type.TypeRef typeRef = (Type.TypeRef)tree.type;
                Symbol sym = typeRef.sym;
                Type[] args = typeRef.args;
                if (sym.kind == Kinds.CLASS)
                    verify(tree,
                           sym.typeParams().length == args.length,
                           "enough arguments for TypeRefs",
                           "Type " + Debug.show(sym)
                           + " expects " + sym.typeParams().length + " type arguments"
                           + " but is given " + args.length);
            }

            if (tree instanceof Tree.ClassDef) {
                checkIsTypeTerm(tree, ((Tree.ClassDef)tree).tpe);
            } else if (tree instanceof Tree.ModuleDef) {
                checkIsTypeTerm(tree, ((Tree.ModuleDef)tree).tpe);
            } else if (tree instanceof Tree.ValDef) {
                checkIsTypeTerm(tree, ((Tree.ValDef)tree).tpe);
            } else if (tree instanceof Tree.DefDef) {
                checkIsTypeTerm(tree, ((Tree.DefDef)tree).tpe);
            } else if (tree instanceof Tree.Typed) {
                checkIsTypeTerm(tree, ((Tree.Typed)tree).tpe);
            }
        }
    }
}
