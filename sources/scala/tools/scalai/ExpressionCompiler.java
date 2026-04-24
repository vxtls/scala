/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: ExpressionCompiler.java,v 1.16 2002/10/04 15:37:10 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

import scalac.ast.Tree;
import scalac.atree.AConstant;
import scalac.backend.Primitives;
import scalac.symtab.Definitions;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;
import scalac.util.Name;

public class ExpressionCompiler {

    //########################################################################
    // Private Fields

    private final Definitions definitions;
    private final Primitives primitives;
    private final Constants constants;
    private final ExpressionContext context;

    //########################################################################
    // Public Constructors

    public ExpressionCompiler(Definitions definitions, Primitives primitives, Constants constants, ExpressionContext context, Symbol[] params) {
        this.definitions = definitions;
        this.primitives = primitives;
        this.constants = constants;
        this.context = context;
        for (int i = 0; i < params.length; i++)
            context.insertVariable(params[i], Variable.Argument(i));
    }

    //########################################################################
    // Public Methods

    public CodeContainer compile(Tree tree) {
        Code code = compute(tree);
        return new CodeContainer(
            context.source(), context.owner(), code, context.stackmax());
    }

    public CodeContainer compile(ArrayList items) {
        Code value = Code.Literal(constants.literal());
        CodeBuffer buffer = new CodeBuffer();
        for (int i = 0, m = items.size(); i < m;) {
            if (i > 0) buffer.append(value);
            Object item = items.get(i++);
            value = compute((Tree)item);
        }
        Code code = buffer.code(value);
        return new CodeContainer(
            context.source(), context.owner(), code, context.stackmax());
    }

    //########################################################################
    // Private Methods - declare

    private void declare(Tree tree, CodeBuffer buffer) {
        if (tree == Tree.Empty) {
            return;
        }
        if (tree instanceof Tree.ValDef) {
            Tree body = ((Tree.ValDef)tree).rhs;
            Symbol symbol = tree.symbol();
            Variable variable = Variable.Local(context.push());
            context.insertVariable(symbol, variable);
            // !!! this should be done in an earlier phase
            Code value = body != Tree.Empty ?
                compute(body) : Code.Literal(constants.zero(symbol.type()));
            buffer.append(Code.Store(Code.Self, variable, value));
            return;
        }
        buffer.append(compute(tree));
    }

    //########################################################################
    // Private Methods - compute

    private Code[] compute(Tree[] trees) {
        Code[] codes = new Code[trees.length];
        for (int i = 0; i < codes.length; i++) codes[i] = compute(trees[i]);
        return codes;
    }

    private Code compute(Tree tree) {
        if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef)tree;
            Tree.Ident[] params = labelDef.params;
            Tree body = labelDef.rhs;
            Symbol symbol = tree.symbol();
            Variable[] vars = new Variable[params.length];
            for (int i = 0; i < params.length; i++) {
                vars[i] = context.lookupVariable(params[i].symbol());
                // !!!
                assert
                    vars[i] instanceof Variable.Argument ||
                    vars[i] instanceof Variable.Local :
                    Debug.show(vars[i]);
            }
            context.insertLabel(symbol);
            return Code.Label(symbol, vars, compute(body));
        }
        if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block)tree;
            if (block.stats.length == 0) return compute(block.expr);
            CodeBuffer buffer = new CodeBuffer();
            int stacksize = context.stacksize();
            for (int i = 0; i < block.stats.length; i++) declare(block.stats[i], buffer);
            Code result = compute(block.expr);
            context.stacksize(stacksize);
            return buffer.code(result);
        }
        if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign)tree;
            return store(assign.lhs, assign.lhs.symbol(), compute(assign.rhs));
        }
        if (tree instanceof Tree.If) {
            Tree.If branch = (Tree.If)tree;
            return Code.If(compute(branch.cond), compute(branch.thenp),
                // !!! can we remove this test ?
                branch.elsep == Tree.Empty ? Code.Literal(constants.literal()) : compute(branch.elsep));
        }
        if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch)tree;
            return Code.Switch(
                compute(switchTree.test), switchTree.tags, compute(switchTree.bodies), compute(switchTree.otherwise));
        }
        if (tree instanceof Tree.New) { // !!!
            Tree.Template templateTree = ((Tree.New)tree).templ;
            Tree[] bases = templateTree.parents;
            Tree[] body = templateTree.body;
            assert bases.length == 1 : Debug.show(tree);
            assert body.length == 0 : Debug.show(tree);
            Symbol symbol = Symbol.NONE.newTerm(tree.pos, 0, Name.fromString("new")); // !!! should be newVariable
            Variable variable = Variable.Local(context.push());
            Code code = compute(bases[0]);
            Template template = context.lookupTemplate(tree.getType().symbol());
            if (template instanceof Template.Global) {
                ScalaTemplate scalaTemplate = ((Template.Global)template).template;
                assert code instanceof Code.Invoke : Debug.show(code);
                Code.Invoke invoke = (Code.Invoke)code;
                // !!! correct ?
                assert invoke.target == Code.Self | invoke.target == Code.Null : Debug.show(code);
                invoke.target = Code.Load(Code.Null, variable);
                context.insertVariable(symbol, variable);
                Code module = scalaTemplate.getSymbol().isModuleClass()
                    && scalaTemplate.getSymbol().isStatic()
                    ? Code.Store(
                        Code.Null,
                        context.lookupVariable(scalaTemplate.getSymbol().module()),
                        Code.Load(Code.Null, variable))
                    : Code.Null;
                code = Code.Block(
                    new Code[] {
                        Code.Store(Code.Null, variable, Code.Create(scalaTemplate)),
                        module,
                        invoke},
                    Code.Load(Code.Null, variable));
            }
            return code;
        }
        if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            if (apply.fun instanceof Tree.TypeApply) {
                Tree.TypeApply typeApply = (Tree.TypeApply)apply.fun;
                return tapply(typeApply.fun, typeApply.fun.symbol(), typeApply.args, apply.args);
            }
            return vapply(apply.fun, apply.fun.symbol(), apply.args);
        }
        if (tree instanceof Tree.This) {
            return Code.Self;
        }
        if (tree instanceof Tree.Literal) {
            AConstant constant = ((Tree.Literal)tree).value;
            if (constant == AConstant.UNIT) {
                return Code.Literal(constants.literal());
            }
            if (constant instanceof AConstant.BooleanValue) {
                return Code.Literal(new Boolean(((AConstant.BooleanValue)constant).value));
            }
            if (constant instanceof AConstant.ByteValue) {
                return Code.Literal(new Byte(((AConstant.ByteValue)constant).value));
            }
            if (constant instanceof AConstant.ShortValue) {
                return Code.Literal(new Short(((AConstant.ShortValue)constant).value));
            }
            if (constant instanceof AConstant.CharValue) {
                return Code.Literal(new Character(((AConstant.CharValue)constant).value));
            }
            if (constant instanceof AConstant.IntValue) {
                return Code.Literal(new Integer(((AConstant.IntValue)constant).value));
            }
            if (constant instanceof AConstant.LongValue) {
                return Code.Literal(new Long(((AConstant.LongValue)constant).value));
            }
            if (constant instanceof AConstant.FloatValue) {
                return Code.Literal(new Float(((AConstant.FloatValue)constant).value));
            }
            if (constant instanceof AConstant.DoubleValue) {
                return Code.Literal(new Double(((AConstant.DoubleValue)constant).value));
            }
            if (constant instanceof AConstant.StringValue) {
                return Code.Literal(new String(((AConstant.StringValue)constant).value));
            }
            if (constant == AConstant.NULL) {
                return Code.Null;
            }
            throw Debug.abort("illegal case", constant);
        }
        return load(tree, tree.symbol());
    }

    private Code object(Tree tree) {
        if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select)tree;
            if (select.qualifier instanceof Tree.Super) {
                return Code.Self;
            }
            return tree.symbol().isStatic() ? Code.Null : compute(select.qualifier);
        }
        if (tree instanceof Tree.Ident) {
            return Code.Null;
        }
        throw Debug.abort("illegal tree", tree);
    }

    //########################################################################
    // Private Methods - apply

    private Code tapply(Tree target, Symbol symbol, Tree[] targs, Tree[]vargs){
        Code object = object(target);
        if (symbol == definitions.ANY_IS || symbol == definitions.ANY_AS) {
            assert targs.length == 1: Debug.show(targs);
            assert vargs.length == 0 : Debug.show(vargs);
            boolean cast = symbol == definitions.ANY_AS;
            Type type = targs[0].type();
            return Code.IsAs(object, type, context.getClass(type), cast);
        }
        if (symbol == primitives.NEW_OARRAY) {
            assert object == Code.Null: object;
            assert targs.length == 1: Debug.show(targs);
            assert vargs.length == 1 : Debug.show(vargs);
            Type arraytype = Type.UnboxedArrayType(targs[0].type());
            Class component = context.getClass(arraytype).getComponentType();
            return Code.CreateArray(component, compute(vargs[0]));
        }
        throw Debug.abort("unknown method", symbol);
    }

    // !!! only used for the hack in vapply => remove !
    private static final Name plus_N  = Name.fromString("$plus");
    private static final Name minus_N = Name.fromString("$minus");

    private Code vapply(Tree target, Symbol symbol, Tree[] trees) {
        // !!! optimize ?
        Code object = object(target);
        if (symbol == definitions.BOOLEAN_OR()) {
            return Code.Or(object, compute(trees[0]));
        }
        if (symbol == definitions.BOOLEAN_AND()) {
            return Code.And(object, compute(trees[0]));
        }
        if (symbol == definitions.OBJECT_SYNCHRONIZED) {
            return Code.Synchronized(object, compute(trees[0]));
        }
        // !!! System.out.println("!!! method: " + Debug.show(symbol));
        // !!! System.out.println("!!! -owner: " + Debug.show(symbol.owner()));
        Function function = context.lookupFunction(symbol);
        if (trees.length == 0
            && (symbol.name == plus_N || symbol.name == minus_N)
            // !!! the following line does not work. why? (because of erasure?)
            // !!! symbol.owner().isSubClass(definitions.DOUBLE_CLASS))
            && (
                symbol.owner().isSubClass(definitions.INT_CLASS) ||
                symbol.owner().isSubClass(definitions.LONG_CLASS) ||
                symbol.owner().isSubClass(definitions.FLOAT_CLASS) ||
                symbol.owner().isSubClass(definitions.DOUBLE_CLASS)))
        {
            function = symbol.name == plus_N ? Function.Pos : Function.Neg;
        }
        if (target instanceof Tree.Select &&
            ((Tree.Select)target).qualifier instanceof Tree.Super) {
            Template template = context.lookupTemplate(symbol.owner());
            if (template instanceof Template.Global) {
                ScalaTemplate template_ = ((Template.Global)template).template;
                function = Function.Global(template_.getMethod(symbol));
            } else if (template instanceof Template.JavaClass) {
                if (!symbol.isInitializer()) {
                    throw Debug.abort("!!! illegal super on java class", symbol);
                }
            } else {
                throw Debug.abort("illegal template", template);
            }
        }
        Code[] args = compute(trees);
        return Code.Invoke(object, function, args, target.pos);
    }

    //########################################################################
    // Private Methods - load & store

    private Code load(Tree target, Symbol symbol) {
        return Code.Load(object(target), context.lookupVariable(symbol));
    }

    private Code store(Tree target, Symbol symbol, Code value) {
        return Code.Store(object(target),context.lookupVariable(symbol),value);
    }

    //########################################################################
}
