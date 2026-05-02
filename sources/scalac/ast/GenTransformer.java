/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.ast;

import scalac.Global;
import scalac.CompilationUnit;
import scalac.ast.Tree.*;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;

/**
 * A tree transformer that rebuilds trees from symbols and transformed types.
 * Unlike {@link Transformer}, this class reconstructs canonical tree shapes
 * instead of copying the existing node instances.
 */
public class GenTransformer {

    public final Global global;
    public final Type.Map map;
    public final TreeGen gen;

    public GenTransformer(Global global) {
        this(global, Type.IdMap);
    }

    public GenTransformer(Global global, Type.Map map) {
        this.global = global;
        this.gen = global.treeGen;
        this.map = map;
    }

    public void apply(CompilationUnit[] units) {
        for (int i = 0; i < units.length; i++) apply(units[i]);
    }

    public void apply(CompilationUnit unit) {
        unit.global.log("transforming " + unit);
        unit.body = transform(unit.body);
    }

    public Symbol getSymbolFor(Tree tree) {
        return tree.symbol();
    }

    public Type[] transform(Type[] types) {
        for (int i = 0; i < types.length; i++) {
            Type type = transform(types[i]);
            if (type == types[i]) continue;
            Type[] clones = new Type[types.length];
            for (int j = 0; j < i; j++) clones[j] = types[j];
            clones[i] = type;
            for (; i < types.length; i++) clones[i] = transform(types[i]);
            return clones;
        }
        return types;
    }

    public Type transform(Type type) {
        return map.apply(type);
    }

    public Tree transform(Tree tree) {
        if (tree == Tree.Empty) {
            return tree;
        } else if (tree instanceof ClassDef) {
            ClassDef node = (ClassDef)tree;
            Symbol symbol = getSymbolFor(tree);
            if (global.currentPhase.id < global.PHASE.ADDCONSTRUCTORS.id()) {
                Template impl = node.impl;
                Symbol implSymbol = getSymbolFor(impl);
                Tree[] parents = transform(impl.parents);
                Tree[] body = transform(impl.body);
                return gen.ClassDef(symbol, parents, implSymbol, body);
            } else {
                return gen.ClassDef(symbol, transform(node.impl.body));
            }
        } else if (tree instanceof PackageDef) {
            PackageDef node = (PackageDef)tree;
            Tree packaged = node.packaged;
            Template impl = node.impl;
            Symbol symbol = getSymbolFor(packaged);
            Tree[] parents = impl.parents;
            Tree[] body = impl.body;
            assert parents.length == 0: tree;
            return gen.PackageDef(symbol, transform(body));
        } else if (tree instanceof ValDef) {
            ValDef node = (ValDef)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.ValDef(symbol, transform(node.rhs));
        } else if (tree instanceof DefDef) {
            DefDef node = (DefDef)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.DefDef(symbol, transform(node.rhs));
        } else if (tree instanceof AbsTypeDef) {
            Symbol symbol = getSymbolFor(tree);
            return gen.AbsTypeDef(symbol);
        } else if (tree instanceof AliasTypeDef) {
            Symbol symbol = getSymbolFor(tree);
            return gen.AliasTypeDef(symbol);
        } else if (tree instanceof Import) {
            Import node = (Import)tree;
            return gen.Import(tree.pos, transform(node.expr), node.selectors);
        } else if (tree instanceof CaseDef) {
            CaseDef node = (CaseDef)tree;
            Tree pat = transform(node.pat);
            Tree guard = transform(node.guard);
            Tree body = transform(node.body);
            return gen.CaseDef(pat, guard, body);
        } else if (tree instanceof LabelDef) {
            LabelDef node = (LabelDef)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.LabelDef(symbol, transform(node.params), transform(node.rhs));
        } else if (tree instanceof Block) {
            Block node = (Block)tree;
            return gen.Block(tree.pos, transform(node.stats), transform(node.expr));
        } else if (tree instanceof Sequence) {
            Tree[] trees = transform(((Sequence)tree).trees);
            Tree seq = new Sequence(trees);
            seq.pos = tree.pos;
            seq.type = tree.type;
            return seq;
        } else if (tree instanceof Alternative) {
            Alternative node = (Alternative)tree;
            Tree[] trees = transform(node.trees);
            if (global.currentPhase.id > global.PHASE.TRANSMATCH.id()) {
                node.trees = trees;
                return tree;
            }
            return global.make.Alternative(tree.pos, trees);
        } else if (tree instanceof Bind) {
            Symbol symbol = getSymbolFor(tree);
            global.nextPhase();
            symbol.setType(tree.type);
            global.prevPhase();
            Tree bind = new ExtBind(symbol, transform(((Bind)tree).rhs));
            bind.type = symbol.getType();
            bind.pos = tree.pos;
            return bind;
        } else if (tree instanceof Visitor) {
            Visitor node = (Visitor)tree;
            CaseDef[] cases = transform(node.cases);
            if (global.currentPhase.id > global.PHASE.TRANSMATCH.id()) {
                node.cases = cases;
                return tree;
            }
            return global.make.Visitor(tree.pos, cases);
        } else if (tree instanceof Assign) {
            Assign node = (Assign)tree;
            return gen.Assign(tree.pos, transform(node.lhs), transform(node.rhs));
        } else if (tree instanceof If) {
            If node = (If)tree;
            Tree cond = transform(node.cond);
            Tree thenp = transform(node.thenp);
            Tree elsep = transform(node.elsep);
            if (tree.type().isSameAs(global.definitions.ANY_TYPE())) {
                global.nextPhase();
                Type type = global.definitions.ANY_TYPE();
                global.prevPhase();
                return gen.If(tree.pos, cond, thenp, elsep, type);
            } else {
                return gen.If(tree.pos, cond, thenp, elsep);
            }
        } else if (tree instanceof Switch) {
            Switch node = (Switch)tree;
            Tree test = transform(node.test);
            Tree[] bodies = transform(node.bodies);
            Tree otherwise = transform(node.otherwise);
            if (tree.type().isSameAs(global.definitions.ANY_TYPE())) {
                global.nextPhase();
                Type type = global.definitions.ANY_TYPE();
                global.prevPhase();
                return gen.Switch(tree.pos, test, node.tags, bodies, otherwise, type);
            } else {
                return gen.Switch(tree.pos, test, node.tags, bodies, otherwise);
            }
        } else if (tree instanceof Return) {
            Return node = (Return)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.Return(tree.pos, symbol, transform(node.expr));
        } else if (tree instanceof New) {
            New node = (New)tree;
            return gen.New(tree.pos, transform(node.init));
        } else if (tree instanceof Create) {
            Create node = (Create)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.Create(tree.pos,
                              transform(node.qualifier),
                              symbol,
                              transform(node.targs));
        } else if (tree instanceof Typed) {
            Typed node = (Typed)tree;
            return gen.Typed(tree.pos, transform(node.expr), transform(node.tpe));
        } else if (tree instanceof TypeApply) {
            TypeApply node = (TypeApply)tree;
            return gen.TypeApply(transform(node.fun), transform(node.args));
        } else if (tree instanceof Apply) {
            Apply node = (Apply)tree;
            return gen.Apply(transform(node.fun), transform(node.args));
        } else if (tree instanceof Super) {
            Symbol symbol = getSymbolFor(tree);
            return gen.Super(tree.pos, symbol);
        } else if (tree instanceof This) {
            Symbol symbol = getSymbolFor(tree);
            return gen.This(tree.pos, symbol);
        } else if (tree instanceof Select) {
            Select node = (Select)tree;
            Symbol symbol = getSymbolFor(tree);
            return gen.Select(tree.pos, transform(node.qualifier), symbol);
        } else if (tree instanceof Ident) {
            Symbol symbol = getSymbolFor(tree);
            return gen.Ident(tree.pos, symbol);
        } else if (tree instanceof Literal) {
            Literal node = (Literal)tree;
            return gen.Literal(tree.pos, node.value);
        } else if (tree instanceof TypeTerm) {
            return gen.mkType(tree.pos, transform(tree.type()));
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    public Tree[] transform(Tree[] ts) {
        for (int i = 0; i < ts.length; i++) {
            Tree t = transform(ts[i]);
            if (t != ts[i]) {
                Tree[] res = new Tree[ts.length];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = t;
                for (; i < ts.length; i++) res[i] = transform(ts[i]);
                return res;
            }
        }
        return ts;
    }

    public ValDef[] transform(ValDef[] ts) {
        for (int i = 0; i < ts.length; i++) {
            Tree t = transform(ts[i]);
            if (t != ts[i]) {
                ValDef[] res = new ValDef[ts.length];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = (ValDef)t;
                for (; i < ts.length; i++) res[i] = (ValDef)transform(ts[i]);
                return res;
            }
        }
        return ts;
    }

    public ValDef[][] transform(ValDef[][] ts) {
        for (int i = 0; i < ts.length; i++) {
            ValDef[] t = transform(ts[i]);
            if (t != ts[i]) {
                ValDef[][] res = new ValDef[ts.length][];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = t;
                for (; i < ts.length; i++) res[i] = transform(ts[i]);
                return res;
            }
        }
        return ts;
    }

    public AbsTypeDef[] transform(AbsTypeDef[] ts) {
        for (int i = 0; i < ts.length; i++) {
            Tree t = transform(ts[i]);
            if (t != ts[i]) {
                AbsTypeDef[] res = new AbsTypeDef[ts.length];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = (AbsTypeDef)t;
                for (; i < ts.length; i++) res[i] = (AbsTypeDef)transform(ts[i]);
                return res;
            }
        }
        return ts;
    }

    public CaseDef[] transform(CaseDef[] ts) {
        for (int i = 0; i < ts.length; i++) {
            Tree t = transform(ts[i]);
            if (t != ts[i]) {
                CaseDef[] res = new CaseDef[ts.length];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = (CaseDef)t;
                for (; i < ts.length; i++) res[i] = (CaseDef)transform(ts[i]);
                return res;
            }
        }
        return ts;
    }

    public Ident[] transform(Ident[] ts) {
        for (int i = 0; i < ts.length; i++) {
            Tree t = transform(ts[i]);
            if (t != ts[i]) {
                Ident[] res = new Ident[ts.length];
                System.arraycopy(ts, 0, res, 0, i);
                res[i++] = (Ident)t;
                for (; i < ts.length; i++) res[i] = (Ident)transform(ts[i]);
                return res;
            }
        }
        return ts;
    }
}
