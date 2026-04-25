/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: AddAccessorsPhase.java,v 1.1 2002/10/17 12:27:11 schinz Exp $
// $Id$

package scalac.transformer;

import java.util.Map;
import java.util.HashMap;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.CompilationUnit;
import scalac.ast.Transformer;
import scalac.ast.Tree;
import scalac.ast.Tree.Template;
import scalac.ast.TreeList;
import scalac.symtab.Modifiers;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Name;
import scalac.util.Debug;


/**
 * This phase adds private accessor fields and methods for all class
 * constructor arguments which are accessed from within the class'
 * methods, or nested classes.
 */
public class AddAccessorsPhase extends Phase {

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public AddAccessorsPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
    }

    //########################################################################
    // Public Methods

    /** Applies this phase to the given compilation units. */
    public void apply(CompilationUnit[] units) {
        treeTransformer.apply(units);
    }

    //########################################################################
    // Private Class - Tree transformer

    /** The tree transformer */
    private final Transformer treeTransformer = new Transformer(global) {

        /** The parameter to accessor method map */
        private final Map/*<Symbol,Symbol>*/ methods = new HashMap();

        /** The current method */
        private Symbol method;

        /** Creates an accessor field symbol for given parameter. */
        private Symbol createAccessorField(Symbol param) {
            int flags = Modifiers.PRIVATE | Modifiers.STABLE;
            Name name = Name.fromString(param.name + "$");
            Symbol owner = param.owner().constructorClass();
            Symbol field = owner.newField(param.pos, flags, name);
            field.setType(param.type());
            owner.members().enterOrOverload(field);
            return field;
        }

        /** Creates an accessor method symbol for given parameter. */
        private Symbol createAccessorMethod(Symbol param) {
            int flags = Modifiers.PRIVATE | Modifiers.STABLE | Modifiers.ACCESSOR;
            Name name = param.name;
            Symbol owner = param.owner().constructorClass();
            Symbol method = owner.newMethod(param.pos, flags, name);
            method.setType(Type.MethodType(Symbol.EMPTY_ARRAY, param.type()));
            owner.members().enterOrOverload(method);
            methods.put(param, method);
            return method;
        }

        /** Transforms the given tree. */
        public Tree transform(Tree tree) {
            if (tree instanceof Tree.ClassDef) {
                Tree.ClassDef classDef = (Tree.ClassDef)tree;
                Template impl = classDef.impl;
                Symbol clasz = tree.symbol();
                // transform parents and body
                Symbol backup = this.method;
                this.method = clasz.primaryConstructor();
                Tree[] parents = transform(impl.parents);
                Tree[] body = transform(impl.body);
                this.method = backup;
                // create accessor field & method trees
                TreeList accessors = new TreeList();
                Symbol[] params = clasz.valueParams();
                for (int i = 0; i < params.length; ++i) {
                    Symbol param = params[i];
                    Symbol method = (Symbol)methods.remove(param);
                    if (method == null) continue;
                    Symbol field = createAccessorField(param);
                    accessors.append(
                        gen.ValDef(
                            field,
                            gen.Ident(param.pos, param)));
                    accessors.append(
                        gen.DefDef(
                            method,
                            gen.Select(gen.This(param.pos, clasz), field)));
                }
                body = Tree.concat(accessors.toArray(), body);
                impl = gen.Template(clasz.pos, impl.symbol(), parents, body);
                return gen.ClassDef(clasz, impl);
            } else if (tree instanceof Tree.DefDef) {
                Symbol backup = this.method;
                this.method = tree.symbol();
                tree = super.transform(tree);
                this.method = backup;
                return tree;
            } else if (tree instanceof Tree.Ident) {
                Symbol symbol = tree.symbol();
                if (!symbol.owner().isPrimaryConstructor()) return super.transform(tree);
                if (symbol.owner() == this.method) return super.transform(tree);
                Symbol method = (Symbol)methods.get(symbol);
                if (method == null) method = createAccessorMethod(symbol);
                Tree qualifier = gen.This(tree.pos, method.owner());
                return gen.Apply(gen.Select(tree.pos, qualifier, method));
            } else if (tree instanceof Tree.Bind) {
                Tree.Bind bind = (Tree.Bind)tree;
                bind.rhs = transform(bind.rhs);
                return tree;
            } else if (tree instanceof Tree.Alternative) {
                Tree.Alternative alternative = (Tree.Alternative)tree;
                alternative.trees = transform(alternative.trees);
                return tree;
            } else if (tree instanceof Tree.CaseDef) {
                Tree.CaseDef caseDef = (Tree.CaseDef)tree;
                caseDef.pat = transform(caseDef.pat);
                caseDef.guard = transform(caseDef.guard);
                caseDef.body = transform(caseDef.body);
                return tree;
            } else if (tree instanceof Tree.Visitor) {
                Tree.Visitor visitor = (Tree.Visitor)tree;
                visitor.cases = (Tree.CaseDef[])transform(visitor.cases);
                return tree;
            }
            return super.transform(tree);
        }

    };

    //########################################################################
}
