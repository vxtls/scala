/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.ast;

import scalac.ApplicationError;
import scalac.util.Name;
import scalac.util.Names;
import scalac.symtab.Type;
import scalac.symtab.Symbol;
import scalac.symtab.Modifiers;

import java.util.LinkedList;

public class TreeInfo {

    public static boolean isTerm(Tree tree) {
	return tree.isTerm();
    }

    public static boolean isType(Tree tree) {
	return tree.isType();
    }

    public static boolean isOwnerDefinition(Tree tree) {
	return tree instanceof Tree.PackageDef ||
	       tree instanceof Tree.ClassDef ||
	       tree instanceof Tree.ModuleDef ||
	       tree instanceof Tree.DefDef ||
	       tree instanceof Tree.Import;
    }

    public static boolean isDefinition(Tree tree) {
	return tree instanceof Tree.PackageDef ||
	       tree instanceof Tree.ClassDef ||
	       tree instanceof Tree.ModuleDef ||
	       tree instanceof Tree.DefDef ||
	       tree instanceof Tree.ValDef ||
	       tree instanceof Tree.AbsTypeDef ||
	       tree instanceof Tree.AliasTypeDef ||
	       tree instanceof Tree.Import;
    }

    public static boolean isDeclaration(Tree tree) {
	if (tree instanceof Tree.DefDef) {
	    return ((Tree.DefDef) tree).rhs == Tree.Empty;
	} else if (tree instanceof Tree.ValDef) {
	    return ((Tree.ValDef) tree).rhs == Tree.Empty;
	} else {
	    return tree instanceof Tree.AbsTypeDef ||
	           tree instanceof Tree.AliasTypeDef;
	}
    }

    /** Is tree a pure definition?
     */
    public static boolean isPureDef(Tree tree) {
	if (tree == Tree.Empty ||
	    tree instanceof Tree.ClassDef ||
	    tree instanceof Tree.ModuleDef ||
	    tree instanceof Tree.DefDef ||
	    tree instanceof Tree.AbsTypeDef ||
	    tree instanceof Tree.AliasTypeDef ||
	    tree instanceof Tree.Import) {
	    return true;
	} else if (tree instanceof Tree.ValDef) {
	    Tree.ValDef valDef = (Tree.ValDef) tree;
	    return (valDef.mods & Modifiers.MUTABLE) == 0 && isPureExpr(valDef.rhs);
	} else {
	    return false;
	}
    }

    /** Is tree a stable & pure expression?
     */
    public static boolean isPureExpr(Tree tree) {
	if (tree == Tree.Empty ||
	    tree instanceof Tree.This ||
	    tree instanceof Tree.Super ||
	    tree instanceof Tree.Literal) {
	    return true;
	} else if (tree instanceof Tree.Ident) {
	    assert tree.type != null : tree.toString();
	    return tree.symbol().isStable();
	} else if (tree instanceof Tree.Select) {
	    Tree.Select select = (Tree.Select) tree;
	    return tree.symbol().isStable() && isPureExpr(select.qualifier);
	} else if (tree instanceof Tree.Apply) {
	    Tree.Apply apply = (Tree.Apply) tree;
	    return isPureExpr(apply.fun) && apply.args.length == 0;
	} else if (tree instanceof Tree.TypeApply) {
	    return isPureExpr(((Tree.TypeApply) tree).fun);
	} else if (tree instanceof Tree.Typed) {
	    return isPureExpr(((Tree.Typed) tree).expr);
	} else {
	    return false;
	}
    }

    /** Is tree a pure constructor?
     */
    public static boolean isPureConstr(Tree tree) {
	if (tree instanceof Tree.Ident || tree instanceof Tree.Select) {
 	    return tree.symbol() != null && tree.symbol().isPrimaryConstructor();
	} else if (tree instanceof Tree.TypeApply) {
	    return isPureConstr(((Tree.TypeApply) tree).fun);
	} else if (tree instanceof Tree.Apply) {
	    Tree.Apply apply = (Tree.Apply) tree;
	    return apply.args.length == 0 && isPureConstr(apply.fun);
	} else {
	    return false;
	}
    }

    /** Is tree a self constructor call?
     */
    public static boolean isSelfConstrCall(Tree tree) {
	if (tree instanceof Tree.Ident) {
	    return ((Tree.Ident) tree).name == Names.CONSTRUCTOR;
	} else if (tree instanceof Tree.TypeApply) {
	    return isSelfConstrCall(((Tree.TypeApply) tree).fun);
	} else if (tree instanceof Tree.Apply) {
	    return isSelfConstrCall(((Tree.Apply) tree).fun);
	} else {
	    return false;
	}
    }

    /** Is tree a variable pattern
     */
    public static boolean isVarPattern(Tree pat) {
	return pat instanceof Tree.Ident &&
	       ((Tree.Ident) pat).name.isVariable();
    }

    /** Is tree a this node which belongs to `enclClass'?
     */
    public static boolean isSelf(Tree tree, Symbol enclClass) {
	return tree instanceof Tree.This &&
	       tree.symbol() == enclClass;
    }

    /** The method symbol of an application node, or Symbol.NONE, if none exists.
     */
    public static Symbol methSymbol(Tree tree) {
	Tree meth = methPart(tree);
	if (meth.hasSymbol()) return meth.symbol();
	else return Symbol.NONE;
    }

    /** The method part of an application node
     */
    public static Tree methPart(Tree tree) {
	if (tree instanceof Tree.Apply) {
	    return methPart(((Tree.Apply) tree).fun);
	} else if (tree instanceof Tree.TypeApply) {
	    return methPart(((Tree.TypeApply) tree).fun);
	} else if (tree instanceof Tree.AppliedType) {
	    return methPart(((Tree.AppliedType) tree).tpe);
	} else {
	    return tree;
	}
    }

    /** The symbol with name `name' imported from import clause `tree'. */
    public static Symbol importedSymbol(Tree tree, Name name) {
        if (tree instanceof Tree.Import) {
            Tree.Import importTree = (Tree.Import)tree;
            Type pre = tree.symbol().type();
            boolean renamed = false;
            Name[] selectors = importTree.selectors;
            for (int i = 0; i < selectors.length; i = i + 2) {
                if (i + 1 < selectors.length && name.toTermName() == selectors[i + 1]) {
                    if (name.isTypeName())
                        return pre.lookupNonPrivate(selectors[i].toTypeName());
                    else
                        return pre.lookupNonPrivate(selectors[i]);
                } else if (name.toTermName() == selectors[i]) {
                    renamed = true;
                } else if (selectors[i] == Names.IMPORT_WILDCARD && !renamed) {
                    return pre.lookupNonPrivate(name);
                }
            }
            return Symbol.NONE;
        }
        throw new ApplicationError();
    }


      /** returns true if the tree is a sequence-valued pattern.
       *  precondition: tree is a pattern.
       *  calls isSequenceValued( Tree, List ) because needs to remember bound
       *  values.
       */
      public static boolean isSequenceValued( Tree tree ) {
            return isSequenceValued( tree, new LinkedList() );
      }

      /** returns true if the tree is a sequence-valued pattern.
       *  precondition: tree is a pattern
       */
      public static boolean isSequenceValued( Tree tree, LinkedList recVars ) {
            if( tree instanceof Tree.Bind ) {
                  Tree t = ((Tree.Bind) tree).rhs;
                  recVars.addFirst( tree.symbol() );
                  boolean res = isSequenceValued( t );
                  recVars.removeFirst();
                  return res;
            } else if( tree instanceof Tree.Sequence ) {
                  return true;
            } else if( tree instanceof Tree.Alternative ) {
                  Tree[] ts = ((Tree.Alternative) tree).trees;
                  for( int i = 0; i < ts.length; i++ ) {
                        if( isSequenceValued( ts[ i ] ) )
                              return true;
                  }
                  return false;
            } else if( tree instanceof Tree.Ident ) {
                  return recVars.contains( tree.symbol() );
            } else if( tree instanceof Tree.Apply ||
                       tree instanceof Tree.Literal ||
	               tree instanceof Tree.Select ||
                       tree instanceof Tree.Typed ) {
		return false;
            } else {
                  throw new scalac.ApplicationError("Unexpected pattern "+tree.getClass());
            }
      }

    /** returns true if the argument is an empty sequence pattern
     */
    public static boolean isEmptySequence( Tree tree ) {
	return tree instanceof Tree.Sequence &&
	       ((Tree.Sequence) tree).trees.length == 0;
    }
      /** this test should correspond to the one used in TransMatch phase */
      public static boolean isRegularPattern( Tree tree ) {
		    if (tree instanceof Tree.Alternative) {
	                  return true;
		    } else if (tree instanceof Tree.Bind) {
	                  return isRegularPattern(((Tree.Bind) tree).rhs);
		    } else if (tree instanceof Tree.Ident) {
	                  return false;
		    } else if (tree instanceof Tree.CaseDef) {
	                  isRegularPattern(((Tree.CaseDef) tree).pat);
		    } else if (tree instanceof Tree.Sequence) {
	                  return true;
            } else if (tree instanceof Tree.Apply) {
	                  Tree[] trees = ((Tree.Apply) tree).args;
	                  for( int i = 0; i < trees.length; i++ )
	                        if( isRegularPattern( trees[i] ) )
	                              return true;
            } else if (tree instanceof Tree.Literal) {
	                  return false;
		    }
            return false;
      }

}
