/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import scalac.Unit;
import scalac.ast.Tree;
import scalac.ast.Tree.Ident;
import scalac.ast.Tree.Template;
import scalac.symtab.Definitions;
import scalac.symtab.Symbol;
import scalac.symtab.TermSymbol;
import scalac.symtab.Type;
import scalac.util.Debug;
import scalac.util.Name;

/** This class translates syntax trees into attributed trees. */
public class ATreeFromSTree {

    //########################################################################
    // Private Fields

    /** The global definitions */
    private final Definitions definitions;

    /** The attributed tree factory */
    private final ATreeFactory make;

    /** A mapping from primitive classes to initialization state */
    private final Map/*<Symbol,Boolean>*/ states;

    /** A mapping from primitive methods to generators */
    private final Map/*<Symbol,Generator>*/ generators;

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public ATreeFromSTree(Definitions definitions) {
        this.definitions = definitions;
        this.make = new ATreeFactory();
        this.states = new HashMap();
        this.generators = new HashMap();
        Symbol[] classes = {
            definitions.ANY_CLASS,
            definitions.JAVA_OBJECT_CLASS,
            definitions.JAVA_STRING_CLASS,
            definitions.JAVA_THROWABLE_CLASS,
            definitions.ARRAY_CLASS,
            definitions.UNIT_CLASS,
            definitions.BOOLEAN_CLASS,
            definitions.BYTE_CLASS,
            definitions.SHORT_CLASS,
            definitions.CHAR_CLASS,
            definitions.INT_CLASS,
            definitions.LONG_CLASS,
            definitions.FLOAT_CLASS,
            definitions.DOUBLE_CLASS,
        };
        for (int i = 0; i < classes.length; i++) {
            states.put(classes[i], Boolean.FALSE);
        }
    }

    //########################################################################
    // Public Methods - Translating units

    /** Translates the unit's body and stores the result in it. */
    public void translate(Unit unit) {
        template(unit.repository = new ARepository(), unit.body);
    }

    //########################################################################
    // Private Methods - Translating templates

    /** Translates the templates and adds them to the repository. */
    private void template(ARepository repository, Tree[] trees) {
        for (int i = 0; i < trees.length; i++) {
            template(repository, trees[i]);
        }
    }

    /** Translates the template and adds it to the repository. */
    private void template(ARepository repository, Tree tree) {
        if (tree == Tree.Empty) {
            return;
        } else if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef) tree;
            AClass clasz = new AClass(tree.symbol());
            // !!! add static field to global modules
            repository.addClass(clasz);
            member(clasz, classDef.impl.body);
            return;
        } else if (tree instanceof Tree.PackageDef) {
            Tree.PackageDef packageDef = (Tree.PackageDef) tree;
            template(repository, packageDef.impl.body);
            return;
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Translating members

    /** Translates the members and adds them to the class. */
    private void member(AClass clasz, Tree[] trees) {
        for (int i = 0; i < trees.length; i++) {
            member(clasz, trees[i]);
        }
    }

    /** Translates the member and adds it to the class. */
    private void member(AClass clasz, Tree tree) {
        if (tree == Tree.Empty) {
            return;
        } else if (tree instanceof Tree.ClassDef) {
            template(clasz, tree);
            return;
        } else if (tree instanceof Tree.ValDef) {
            AField field = new AField(tree.symbol(), false);
            clasz.addField(field);
            return;
        } else if (tree instanceof Tree.DefDef) {
            Tree.DefDef defDef = (Tree.DefDef) tree;
            AMethod method = new AMethod(tree.symbol(), false);
            clasz.addMethod(method);
            if (!method.isAbstract()) {
                method.setCode(expression(defDef.rhs));
            }
            return;
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Translating statements

     /** Translates the statements. */
    private ACode[] statement(List locals, Tree[] trees) {
        List codes = new ArrayList();
        for (int i = 0; i < trees.length; i++) {
            ACode code = statement(locals, trees[i]);
            if (code != ACode.Void) {
                codes.add(code);
            }
        }
        return (ACode[])codes.toArray(new ACode[codes.size()]);
    }

    /** Translates the statement. */
    private ACode statement(List locals, Tree tree) {
        if (tree == Tree.Empty) {
            return make.Void;
        } else if (tree instanceof Tree.ValDef) {
            Tree.ValDef valDef = (Tree.ValDef) tree;
            Symbol symbol = tree.symbol();
            locals.add(symbol);
            ALocation location = ALocation.Local(symbol, false);
            return make.Store(tree, location, expression(valDef.rhs));
        } else {
            return ACode.Drop(expression(tree), tree.type());
        }
    }

    //########################################################################
    // Private Methods - Translating expressions

    /** Translates the expressions. */
    private ACode[] expression(Tree[] trees) {
        ACode[] codes = new ACode[trees.length];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = expression(trees[i]);
        }
        return codes;
    }

    /** Translates the expression. */
    private ACode expression(Tree tree) {
        if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef) tree;
            Symbol[] locals = Tree.symbolOf(labelDef.params);
            return make.Label(tree, tree.symbol(), locals, expression(labelDef.rhs));
        } else if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block) tree;
            List locals = new ArrayList();
            ACode[] codes = statement(locals, block.stats);
            ACode code = expression(block.expr);
            if (locals.size() == 0 && codes.length == 0) {
                return code;
            }
            Symbol[] symbols =
                (Symbol[])locals.toArray(new Symbol[locals.size()]);
            return make.Block(tree, symbols, codes, code);
        } else if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign) tree;
            return make.Block(tree, Symbol.EMPTY_ARRAY, new ACode[] {
                make.Store(tree, location(assign.lhs), expression(assign.rhs))},
                make.Void);
        } else if (tree instanceof Tree.If) {
            Tree.If ifTree = (Tree.If) tree;
            ACode test = expression(ifTree.cond);
            return make.If(tree, test, expression(ifTree.thenp), expression(ifTree.elsep));
        } else if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch) tree;
            int[][] tagss = new int[switchTree.tags.length][];
            for (int i = 0; i < tagss.length; i++) {
                tagss[i] = new int[] {switchTree.tags[i]};
            }
            ACode[] codes = new ACode[switchTree.bodies.length + 1];
            for (int i = 0; i < switchTree.bodies.length; i++) {
                codes[i] = expression(switchTree.bodies[i]);
            }
            codes[switchTree.tags.length] = expression(switchTree.otherwise);
            return make.Switch(tree, expression(switchTree.test), tagss, codes);
        } else if (tree instanceof Tree.Return) {
            return make.Return(tree, tree.symbol(), expression(((Tree.Return) tree).expr));
        } else if (tree instanceof Tree.Throw) {
            return make.Throw(tree, expression(((Tree.Throw) tree).expr));
        } else if (tree instanceof Tree.New) {
            Tree.New newTree = (Tree.New) tree;
            Tree[] bases = newTree.templ.parents;
            Tree base = bases[0];
            if (base instanceof Tree.Apply) {
                Tree.Apply apply = (Tree.Apply) base;
                if (apply.fun instanceof Tree.TypeApply) {
                    Tree.TypeApply typeApply = (Tree.TypeApply) apply.fun;
                    return apply(tree, method(typeApply.fun), typeApply.args, apply.args);
                }
                return apply(tree, method(apply.fun), Tree.EMPTY_ARRAY, apply.args);
            }
            throw Debug.abort("illegal case", base);
        } else if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply) tree;
            if (apply.fun instanceof Tree.TypeApply) {
                Tree.TypeApply typeApply = (Tree.TypeApply) apply.fun;
                return apply(tree, typeApply.fun, typeApply.args, apply.args);
            }
            return apply(tree, apply.fun, Tree.EMPTY_ARRAY, apply.args);
        } else if (tree instanceof Tree.Super || tree instanceof Tree.This) {
            return make.This(tree, tree.symbol());
        } else if (tree instanceof Tree.Select || tree instanceof Tree.Ident) {
            return make.Load(tree, location(tree));
        } else if (tree instanceof Tree.Literal) {
            Tree.Literal literal = (Tree.Literal) tree;
            return make.Constant(tree, literal.value);
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    /** Translates the application. */
    private ACode apply(Tree tree, Tree fun, Tree[] targs, Tree[] vargs) {
        if (fun instanceof Tree.Ident) {
            return make.Goto(tree, fun.symbol(), expression(vargs));
        } else {
            return apply(tree, method(fun), targs, vargs);
        }
    }

    /** Translates the application. */
    private ACode apply(Tree tree, AFunction function, Tree[] targs, Tree[] vargs) {
        Type[] types = Tree.typeOf(targs);
        if (function instanceof AFunction.Method) {
            AFunction.Method methodFunction = (AFunction.Method) function;
            if (methodFunction.style.isDynamic()) {
                Symbol clasz = methodFunction.method.owner();
                Object state = states.get(clasz);
                if (state != null) {
                    if (state != Boolean.TRUE) {
                        addGeneratorsOf(clasz);
                    }
                    Object generator = generators.get(methodFunction.method);
                    if (generator != null) {
                        return generate(
                            (Generator)generator,
                            tree,
                            methodFunction.object,
                            types,
                            vargs);
                    }
                }
            }
        }
        return make.Apply(tree, function, types, expression(vargs));
    }

    //########################################################################
    // Private Methods - Translating functions

    /** Translates the method. */
    private AFunction method(Tree tree) {
        Symbol symbol = tree.symbol();
        if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select) tree;
            if (symbol.isJava() && symbol.owner().isModuleClass()) {
                return AFunction.Method(make.Void, symbol, AInvokeStyle.StaticClass);
            }
            ACode object = expression(select.qualifier);
            return AFunction.Method(object, symbol, invokeStyle(select.qualifier));
        } else if (tree instanceof Tree.Ident) {
            return AFunction.Method(make.Void, symbol, AInvokeStyle.New);
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    /** Returns the InvokeStyle to use for the qualifier. */
    private AInvokeStyle invokeStyle(Tree qualifier) {
        if (qualifier instanceof Tree.Super) {
            return AInvokeStyle.StaticInstance;
        } else {
            return AInvokeStyle.Dynamic;
        }
    }

    //########################################################################
    // Private Methods - Translating locations

    /** Translates the location. */
    private ALocation location(Tree tree) {
        Symbol symbol = tree.symbol();
        if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select) tree;
            if (symbol.isModule()) {
                return ALocation.Module(symbol);
            }
            if (symbol.isJava() && symbol.owner().isModuleClass()) {
                return ALocation.Field(make.Void, symbol, true);
            }
            return ALocation.Field(expression(select.qualifier), symbol, false);
        } else if (tree instanceof Tree.Ident) {
            if (symbol.isModule()) {
                return ALocation.Module(symbol);
            }
            return ALocation.Local(symbol, symbol.isParameter());
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Translating constants

    /** Translates the constant. */
    private AConstant constant(Object value) {
        if (value instanceof Boolean  ) return make.BOOLEAN((Boolean  )value);
        if (value instanceof Byte     ) return make.BYTE   (((Byte    )value));
        if (value instanceof Short    ) return make.SHORT  ((Short    )value);
        if (value instanceof Character) return make.CHAR   ((Character)value);
        if (value instanceof Integer  ) return make.INT    ((Integer  )value);
        if (value instanceof Long     ) return make.LONG   ((Long     )value);
        if (value instanceof Float    ) return make.FLOAT  ((Float    )value);
        if (value instanceof Double   ) return make.DOUBLE ((Double   )value);
        if (value instanceof String   ) return make.STRING ((String   )value);
        throw Debug.abort("illegal constant", value + " -- " + value.getClass());
    }

    //########################################################################
    // Private Methods - Generating code for primitive methods

    /** Applies generator to given object and arguments. */
    private ACode generate(Generator generator, Tree tree, ACode object,
        Type[] targs, Tree[] vargs)
    {
        if (generator == Generator.ANYID) {
            assert targs.length == 0 && vargs.length == 1: tree;
            return make.EQ(tree, ATypeKind.REF, object, expression(vargs[0]));
        } else if (generator == Generator.ANYEQ) {
            Symbol lf = newLocal(tree, definitions.ANY_TYPE());
            Symbol rg = newLocal(tree, definitions.ANY_TYPE());
            return make.Block(tree,
                new Symbol[] {lf, rg},
                new ACode[] {
                    store(tree, lf, object),
                    store(tree, rg, expression(vargs[0]))},
                make.If(tree,
                    make.EQ(tree, ATypeKind.REF, load(tree, lf)),
                    make.EQ(tree, ATypeKind.REF, load(tree, rg)),
                    make.Apply(tree,
                        AFunction.Method(
                            load(tree, lf),
                            definitions.ANY_EQUALS,
                            AInvokeStyle.Dynamic),
                        Type.EMPTY_ARRAY,
                        new ACode[] {load(tree, rg)})));
        } else if (generator == Generator.ANYNE) {
            Symbol lf = newLocal(tree, definitions.ANY_TYPE());
            Symbol rg = newLocal(tree, definitions.ANY_TYPE());
            return make.Block(tree,
                new Symbol[] {lf, rg},
                new ACode[] {
                    store(tree, lf, object),
                    store(tree, rg, expression(vargs[0]))},
                make.If(tree,
                    make.EQ(tree, ATypeKind.REF, load(tree, lf)),
                    make.NE(tree, ATypeKind.REF, load(tree, rg)),
                    make.NOT(tree,
                        ATypeKind.BOOL,
                        make.Apply(tree,
                            AFunction.Method(
                                load(tree, lf),
                                definitions.ANY_EQUALS,
                                AInvokeStyle.Dynamic),
                            Type.EMPTY_ARRAY,
                            new ACode[] {load(tree, rg)}))));
        } else if (generator.isIsAs()) {
            assert targs.length == 1 && vargs.length == 0: tree;
            return make.IsAs(tree, object, targs[0], generator.cast());
        } else if (generator == Generator.SYNCHRONIZED) {
            assert targs.length == 1 && vargs.length == 1: tree;
            return make.Synchronized(tree, object, expression(vargs[0]));
        } else if (generator == Generator.THROW) {
            assert targs.length == 0 && vargs.length == 0: tree;
            return make.Throw(tree, object);
        } else if (generator.isConcat()) {
            assert targs.length == 0 && vargs.length == 1: tree;
            ATypeKind suffix = kind(vargs[0].type());
            ACode argument = expression(vargs[0]);
            return make.CONCAT(tree, generator.prefix(), suffix, object, argument);
        } else {
            throw Debug.abort("unknown case", generator);
        }
    }

    /** Generates a load operation with given variable. */
    private ACode load(Tree tree, Symbol local) {
        assert local.owner().isNone(): Debug.show(local);
        return make.Load(tree, ALocation.Local(local, false));
    }

    /** Generates a store operation with given variable and value. */
    private ACode store(Tree tree, Symbol local, ACode value) {
        assert local.owner().isNone(): Debug.show(local);
        return make.Store(tree, ALocation.Local(local, false), value);
    }

    /** Creates a variable with tree's position and given type. */
    private Symbol newLocal(Tree tree, Type type) {
        Symbol owner = Symbol.NONE; // !!!
        Name name = Name.fromString("local"); // !!!
        return new TermSymbol(tree.pos, name, owner, 0).setType(type);
    }

    /** Returns the type kind of given type. */
    private ATypeKind kind(Type type) {
        if (type instanceof Type.SingleType || type instanceof Type.ConstantType) {
            return kind(type.singleDeref());
        } else if (type instanceof Type.TypeRef) {
            Symbol clasz = type.symbol();
            if (clasz == definitions.BOOLEAN_CLASS) return ATypeKind.BOOL;
            if (clasz == definitions.BYTE_CLASS) return ATypeKind.I1;
            if (clasz == definitions.SHORT_CLASS) return ATypeKind.I2;
            if (clasz == definitions.CHAR_CLASS) return ATypeKind.U2;
            if (clasz == definitions.INT_CLASS) return ATypeKind.I4;
            if (clasz == definitions.LONG_CLASS) return ATypeKind.I8;
            if (clasz == definitions.FLOAT_CLASS) return ATypeKind.R4;
            if (clasz == definitions.DOUBLE_CLASS) return ATypeKind.R8;
            if (clasz == definitions.JAVA_STRING_CLASS) return ATypeKind.STR;
            return ATypeKind.REF;
        } else {
            return ATypeKind.REF;
        }
    }

    //########################################################################
    // Private Methods - Collecting primitive methods

    /** Associates generators to primitive methods of given class. */
    private void addGeneratorsOf(Symbol clasz) {
        if (clasz == definitions.ANY_CLASS) {
            addGenerator(definitions.ANY_EQ, Generator.ANYID);
            addGenerator(definitions.ANY_EQEQ, Generator.ANYEQ);
            addGenerator(definitions.ANY_BANGEQ, Generator.ANYNE);
            addGenerator(definitions.ANY_IS, Generator.ISAS(false));
            addGenerator(definitions.ANY_AS, Generator.ISAS(true));
        }
        if (clasz == definitions.JAVA_OBJECT_CLASS) {
            addGenerator(definitions.ANYREF_SYNCHRONIZED, Generator.SYNCHRONIZED);
        }
        if (clasz == definitions.JAVA_STRING_CLASS) {
            addGenerator(definitions.JAVA_STRING_PLUS, Generator.CONCAT(ATypeKind.STR));
        }
        if (clasz == definitions.JAVA_THROWABLE_CLASS) {
            addGenerator(definitions.JAVA_THROWABLE_THROW, Generator.THROW);
        }
        if (clasz == definitions.ARRAY_CLASS) {
            // !!! addAll(defs.ARRAY_CLASS, Names.length, Primitive.LENGTH, 1);
            // !!! addAll(defs.ARRAY_CLASS, Names.apply, Primitive.APPLY, 2);
            // !!! addAll(defs.ARRAY_CLASS, Names.update, Primitive.UPDATE, 1);
        }
        if (clasz == definitions.UNIT_CLASS) {
            // !!!
        }
        if (clasz == definitions.BOOLEAN_CLASS) {
            // !!!
        }
        if (clasz == definitions.BYTE_CLASS) {
            // !!!
        }
        if (clasz == definitions.SHORT_CLASS) {
            // !!!
        }
        if (clasz == definitions.CHAR_CLASS) {
            // !!!
        }
        if (clasz == definitions.INT_CLASS) {
            // !!!
        }
        if (clasz == definitions.LONG_CLASS) {
            // !!!
        }
        if (clasz == definitions.FLOAT_CLASS) {
            // !!!
        }
        if (clasz == definitions.DOUBLE_CLASS) {
            // !!!
        }
        states.put(clasz, Boolean.TRUE);
    }

    /** Associates given generator to given primitive method. */
    private void addGenerator(Symbol method, Generator generator) {
        generators.put(method, generator);
    }

    //########################################################################
    // Private Class - Code generators

    /** Code generators for primitive methods. */
    private static class Generator {
        private final String name;
        private final Boolean cast;
        private final ATypeKind prefix;

        private Generator(String name, Boolean cast, ATypeKind prefix) {
            this.name = name;
            this.cast = cast;
            this.prefix = prefix;
        }

        private static final Generator ANYID =
            new Generator("ANYID", null, null);
        private static final Generator ANYEQ =
            new Generator("ANYEQ", null, null);
        private static final Generator ANYNE =
            new Generator("ANYNE", null, null);
        private static final Generator SYNCHRONIZED =
            new Generator("SYNCHRONIZED", null, null);
        private static final Generator THROW =
            new Generator("THROW", null, null);
        private static final Generator ISAS_FALSE =
            new Generator("ISAS", Boolean.FALSE, null);
        private static final Generator ISAS_TRUE =
            new Generator("ISAS", Boolean.TRUE, null);

        private static Generator ISAS(boolean cast) {
            return cast ? ISAS_TRUE : ISAS_FALSE;
        }

        private static Generator CONCAT(ATypeKind prefix) {
            return new Generator("CONCAT", null, prefix);
        }

        private boolean isIsAs() {
            return "ISAS".equals(name);
        }

        private boolean cast() {
            return cast.booleanValue();
        }

        private boolean isConcat() {
            return "CONCAT".equals(name);
        }

        private ATypeKind prefix() {
            return prefix;
        }

        public String toString() {
            return name;
        }
    }

    //########################################################################
}
