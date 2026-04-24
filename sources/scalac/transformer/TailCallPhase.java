/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.transformer;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.Unit;
import scalac.ast.Tree;
import scalac.ast.GenTransformer;
import scalac.symtab.Modifiers;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;

/**
 * A Tail Call Transformer
 *
 * @author     Erik Stenman
 * @version    1.0
 *
 * What it does:
 *
 * Finds method calls in tail-position and replaces them with jumps.
 * A call is in a tail-position if it is the last instruction to be
 * executed in the body of a method.  This is done by recursing over
 * the trees that may contain calls in tail-position (trees that can't
 * contain such calls are not transformed).
 *
 * Self-recursive calls in tail-position are replaced by jumps to a
 * label at the beginning of the method. As the JVM provides no way to
 * jump from a method to another one, non-recursive calls in
 * tail-position are not optimized.
 *
 * A method call is self-recursive if it calls the current method on
 * the current instance and the method is final (otherwise, it could
 * be a call to an overridden method in a subclass). Furthermore, If
 * the method has type parameters, the call must contain these
 * parameters as type arguments.
 *
 * If a method contains self-recursive calls, a label is added to at
 * the beginning of its body and the calls are replaced by jumps to
 * that label.
 */
public class TailCallPhase extends Phase {

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public TailCallPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
    }

   //########################################################################
    // Public Methods

    /** Applies this phase to the given compilation units. */
    public void apply(Unit[] units) {
        treeTransformer.apply(units);
    }

   //########################################################################
    // Private Classes

    /** The tree transformer */
    private final GenTransformer treeTransformer = new GenTransformer(global) {

        /** The current method */
        private Symbol method;

        /** The current tail-call label */
        private Symbol label;

        /** The expected type arguments of self-recursive calls */
        private Type[] types;

        /** Transforms the given tree. */
        public Tree transform(Tree tree) {
            if (tree instanceof Tree.DefDef) {
                Tree rhs = ((Tree.DefDef)tree).rhs;
                assert method == null: Debug.show(method) + " -- " + tree;
                method = tree.symbol();
                if (method.isMethodFinal()) {
                    label = method.newLabel(method.pos, method.name);
                    types = Type.EMPTY_ARRAY;
                    Type type = method.type();
                    if (type instanceof Type.PolyType) {
                        Type.PolyType polyType = (Type.PolyType)type;
                        types = Symbol.type(polyType.tparams);
                        type = polyType.result;
                    }
                    label.setInfo(type.cloneType(method, label));
                    rhs = transform(rhs);
                    if (label.isAccessed()) {
                        rhs = gen.LabelDef(label, method.valueParams(), rhs);
                        tree = gen.DefDef(method, rhs);
                    }
                    types = null;
                    label = null;
                }
                method = null;
                return tree;
            }
            if (tree instanceof Tree.Block) {
                Tree.Block block = (Tree.Block)tree;
                return gen.Block(tree.pos, block.stats, transform(block.expr));
            }
            if (tree instanceof Tree.If) {
                Tree.If ifTree = (Tree.If)tree;
                Type type = tree.type();
                Tree thenp = transform(ifTree.thenp);
                Tree elsep = transform(ifTree.elsep);
                return gen.If(tree.pos, ifTree.cond, thenp, elsep, type);
            }
            if (tree instanceof Tree.Switch) {
                Tree.Switch switchTree = (Tree.Switch)tree;
                Type type = tree.type();
                Tree[] bodies = transform(switchTree.bodies);
                Tree otherwise = transform(switchTree.otherwise);
                return gen.Switch(tree.pos, switchTree.test, switchTree.tags, bodies, otherwise, type);
            }
            if (tree instanceof Tree.Apply) {
                Tree.Apply apply = (Tree.Apply)tree;
                Tree fun = apply.fun;
                Tree[] vargs = apply.args;
                if (fun instanceof Tree.TypeApply) {
                    Tree.TypeApply typeApply = (Tree.TypeApply)fun;
                    if (method == null || types == null) return tree;
                    Type[] argTypes = Tree.typeOf(typeApply.args);
                    if (!Type.isSameAs(argTypes, types)) return tree;
                    return transform(tree, typeApply.fun, vargs);
                }
                return transform(tree, fun, vargs);
            }
            if (tree instanceof Tree.ClassDef
                || tree instanceof Tree.PackageDef
                || tree instanceof Tree.LabelDef
                || tree instanceof Tree.Return) {
                return super.transform(tree);
            }
            if (tree == Tree.Empty
                || tree instanceof Tree.ValDef
                || tree instanceof Tree.Assign
                || tree instanceof Tree.New
                || tree instanceof Tree.Super
                || tree instanceof Tree.This
                || tree instanceof Tree.Select
                || tree instanceof Tree.Ident
                || tree instanceof Tree.Literal
                || tree instanceof Tree.TypeTerm) {
                return tree;
            }
            throw Debug.abort("illegal case", tree);
        }

        /** Transforms the given function call. */
        private Tree transform(Tree tree, Tree fun, Tree[] vargs) {
            Symbol symbol = fun.symbol();
            if (symbol != method) return tree;
            if (fun instanceof Tree.Select) {
                Tree qual = ((Tree.Select)fun).qualifier;
                if (!isReferenceToThis(qual, method.owner())) return tree;
                return gen.Apply(tree.pos, gen.Ident(qual.pos, label), vargs);
            }
            if (fun instanceof Tree.Ident) {
                assert fun.symbol().isLabel();
                return tree;
            }
            throw Debug.abort("illegal case", fun);
        }

        /**
         * Returns true if the tree represents the current instance of
         * given class.
         */
        private boolean isReferenceToThis(Tree tree, Symbol clasz) {
            if (tree instanceof Tree.This) {
                assert tree.symbol() == clasz: tree +" -- "+ Debug.show(clasz);
                return true;
            }
            if (tree instanceof Tree.Select) {
                Tree qual = ((Tree.Select)tree).qualifier;
                if (!clasz.isModuleClass()) return false;
                if (tree.symbol() != clasz.sourceModule()) return false;
                return isReferenceToThis(qual, clasz.owner());
            }
            if (tree instanceof Tree.Ident) {
                if (!clasz.isModuleClass()) return false;
                if (tree.symbol() != clasz.sourceModule()) return false;
                return true;
            }
            return false;
        }

    };

    //########################################################################
}
