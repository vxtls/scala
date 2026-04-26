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
import scalac.CompilationUnit;
import scalac.ast.Tree;
import scalac.ast.GenTransformer;
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
 * contain such calls are not transformed). However, they are not that
 * many.
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
 * Nested functions can be tail recursive (if this phase runs before
 * lambda lift) and they are transformed as well.
 *
 * If a method contains self-recursive calls, a label is added to at
 * the beginning of its body and the calls are replaced by jumps to
 * that label.
 */
public class TailCallPhase extends Phase {

    private static class Context {
        /** The current method */
        public Symbol method = Symbol.NONE;

        /** The current tail-call label */
        public Symbol label = Symbol.NONE;

        /** The expected type arguments of self-recursive calls */
        public Type[] types;

        /** Tells whether we are in a tail position. */
        public boolean tailPosition;

        public Context() {
            this.tailPosition = false;
        }

        /**
         * The Context of this transformation. It contains the current enclosing method,
         * the label and the type parameters of the enclosing method, together with a
         * flag which says whether our position in the tree allows tail calls. This is
         * modified only in <code>Block</code>, where stats cannot possibly contain tail
         * calls, but the value can.
         */
        public Context(Symbol method, Symbol label, Type[] types, boolean tailPosition) {
            this.method = method;
            this.label  = label;
            this.types  = types;
            this.tailPosition = tailPosition;
        }
    }


    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public TailCallPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
    }

   //########################################################################
    // Public Methods

    /** Applies this phase to the given compilation unit. */
    public void apply(CompilationUnit unit) {
        treeTransformer.apply(unit);
    }

   //########################################################################
    // Private Classes

    /** The tree transformer */
    private final GenTransformer treeTransformer = new GenTransformer(global) {


        /** The context of this call */
        private Context ctx = new Context();

        /** Transform the given tree, which is (or not) in tail position */
        public Tree transform(Tree tree, boolean tailPos) {
            boolean oldTP = ctx.tailPosition;
            ctx.tailPosition = tailPos;
            tree = transform(tree);
            ctx.tailPosition = oldTP;
            return tree;
        }

        public Tree[] transform(Tree[] trees, boolean tailPos) {
            boolean oldTP = ctx.tailPosition;
            ctx.tailPosition = tailPos;
            trees = transform(trees);
            ctx.tailPosition = oldTP;
            return trees;
        }

        /** Transforms the given tree. */
        public Tree transform(Tree tree) {
            if (tree instanceof Tree.DefDef) {
                Tree rhs = ((Tree.DefDef)tree).rhs;
                Context oldCtx = ctx;

                ctx = new Context();

                ctx.method = tree.symbol();
                ctx.tailPosition = true;

                if (ctx.method.isMethodFinal() || ctx.method.owner().isMethod()) {
                    ctx.label = ctx.method.newLabel(ctx.method.pos, ctx.method.name);
                    ctx.types = Type.EMPTY_ARRAY;
                    Type type = ctx.method.type();
                    if (type instanceof Type.PolyType) {
                        Type.PolyType polyType = (Type.PolyType)type;
                        ctx.types = Symbol.type(polyType.tparams);
                        type = polyType.result;
                    }
                    ctx.label.setInfo(type.cloneType(ctx.method, ctx.label));
                    rhs = transform(rhs);
                    if (ctx.label.isAccessed()) {
                        global.log("Rewriting def " + ctx.method.simpleName());
                        rhs = gen.LabelDef(ctx.label, ctx.method.valueParams(), rhs);
                    }
                    tree = gen.DefDef(ctx.method, rhs);
                } else {
                    assert !ctx.method.isMethodFinal()
                        : "Final method: " + ctx.method.simpleName();
                    ctx.tailPosition = false;
                    tree = gen.DefDef(tree.symbol(), transform(rhs));
                }
                ctx = oldCtx;
                return tree;
            }
            if (tree instanceof Tree.Block) {
                Tree.Block block = (Tree.Block)tree;
                boolean oldPosition = ctx.tailPosition;
                ctx.tailPosition = false;
                Tree[] stats = transform(block.stats);
                ctx.tailPosition = oldPosition;
                return gen.Block(tree.pos, stats, transform(block.expr));
            }
            if (tree instanceof Tree.If) {
                Tree.If ifTree = (Tree.If)tree;
                Tree thenp = transform(ifTree.thenp);
                Tree elsep = transform(ifTree.elsep);
                return gen.If(tree.pos, ifTree.cond, thenp, elsep);
            }
            if (tree instanceof Tree.Switch) {
                Tree.Switch switchTree = (Tree.Switch)tree;
                Tree[] bodies = transform(switchTree.bodies);
                Tree otherwise = transform(switchTree.otherwise);
                return gen.Switch(tree.pos,
                                  switchTree.test,
                                  switchTree.tags,
                                  bodies,
                                  otherwise,
                                  tree.type());
            }
            if (tree instanceof Tree.Apply) {
                Tree.Apply apply = (Tree.Apply)tree;
                Tree fun = apply.fun;
                Tree[] vargs = apply.args;

                if (fun instanceof Tree.Select) {
                    Tree.Select select = (Tree.Select)fun;
                    if (select.selector == scalac.util.Names._match) {
                        Tree newTree =
                            global.make.Apply(tree.pos, fun, transform(vargs));
                        newTree.setType(tree.getType());
                        return newTree;
                    }
                }

                if (fun instanceof Tree.TypeApply) {
                    Tree.TypeApply typeApply = (Tree.TypeApply)fun;
                    if (ctx.method != Symbol.NONE && ctx.tailPosition) {
                        Tree[] targs = typeApply.args;
                        assert targs != null : "Null type arguments " + tree;
                        assert ctx.types != null : "Null types " + tree;

                        if (!Type.isSameAs(Tree.typeOf(targs), ctx.types)
                            || !ctx.tailPosition) {
                            return tree;
                        }
                        return transform(tree,
                                         typeApply.fun,
                                         transform(vargs, false));
                    } else {
                        return tree;
                    }
                }
                if (ctx.tailPosition)
                    return transform(tree, fun, transform(vargs, false));
                else {
                    return gen.mkApply_V(fun, transform(vargs, false));
                }
            }
            if (tree instanceof Tree.Visitor) {
                Tree.Visitor visitor = (Tree.Visitor)tree;
                Tree newTree =
                    global.make.Visitor(tree.pos, super.transform(visitor.cases));
                newTree.setType(tree.getType());
                return newTree;
            }
            if (tree instanceof Tree.CaseDef) {
                Tree.CaseDef caseDef = (Tree.CaseDef)tree;
                return gen.CaseDef(caseDef.pat,
                                   caseDef.guard,
                                   transform(caseDef.body));
            }
            if (tree instanceof Tree.Typed) {
                Tree.Typed typed = (Tree.Typed)tree;
                return gen.Typed(transform(typed.expr), typed.tpe);
            }
            if (tree instanceof Tree.ClassDef) {
                Tree.ClassDef classDef = (Tree.ClassDef)tree;
                Tree.Template impl = classDef.impl;
                Symbol implSymbol = getSymbolFor(impl);
                Tree[] body = transform(impl.body);
                return gen.ClassDef(getSymbolFor(tree),
                                    impl.parents,
                                    implSymbol,
                                    body);
            }
            if (tree instanceof Tree.PackageDef
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
                || tree instanceof Tree.TypeTerm
                || tree instanceof Tree.AbsTypeDef
                || tree instanceof Tree.AliasTypeDef
                || tree instanceof Tree.Import
                || tree instanceof Tree.Function) {
                return tree;
            }
            throw Debug.abort("illegal case", tree);
        }

        /** Transforms the given function call. */
        private Tree transform(Tree tree, Tree fun, Tree[] vargs) {
            if (fun.symbol() != ctx.method)
                return tree;
            if (fun instanceof Tree.Select) {
                Tree qual = ((Tree.Select)fun).qualifier;
                if (!isReferenceToThis(qual, ctx.method.owner()))
                    return tree;
                global.log("Applying tail call recursion elimination for " +
                           ctx.method.enclClass().simpleName() + "." + ctx.method.simpleName());
                return gen.Apply(tree.pos, gen.Ident(qual.pos, ctx.label), vargs);
            }
            if (fun instanceof Tree.Ident) {
                global.log("Applying tail call recursion elimination for function " +
                           ctx.method.enclClass().simpleName() + "." + ctx.method.simpleName());
                return gen.Apply(tree.pos, gen.Ident(fun.pos, ctx.label), vargs);
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
            return false;
        }

    };

    //########################################################################
}
