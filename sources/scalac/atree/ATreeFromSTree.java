/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import java.util.List;
import java.util.ArrayList;

import scalac.Unit;
import scalac.ast.Tree;
import scalac.ast.Tree.Ident;
import scalac.ast.Tree.Template;
import scalac.symtab.Definitions;
import scalac.symtab.Symbol;
import scalac.util.Debug;

/** This class translates syntax trees into attributed trees. */
public class ATreeFromSTree {

    //########################################################################
    // Private Fields

    /** The global definitions */
    private final Definitions definitions;

    /** The attributed tree factory */
    private final ATreeFactory make;

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public ATreeFromSTree(Definitions definitions) {
        this.definitions = definitions;
        this.make = new ATreeFactory();
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
        for (int i = 0; i < trees.length; i++) template(repository, trees[i]);
    }

    /** Translates the template and adds it to the repository. */
    private void template(ARepository repository, Tree tree) {
        if (tree == Tree.Empty) {
            return;
        } else if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef) tree;
            AClass clasz = new AClass(tree.symbol());
            repository.addClass(clasz);
            member(clasz, classDef.impl.body);
            return;
        } else if (tree instanceof Tree.PackageDef) {
            template(repository, ((Tree.PackageDef) tree).impl.body);
            return;
        } else if (tree instanceof Tree.ValDef) {
            // !!!
            return;
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Translating members

    /** Translates the members and adds them to the class. */
    private void member(AClass clasz, Tree[] trees) {
        for (int i = 0; i < trees.length; i++) member(clasz, trees[i]);
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
            if (!method.isAbstract()) method.setCode(expression(defDef.rhs));
            return;
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    //########################################################################
    // Private Methods - Translating statements

     /** Translates the statements. */
    private ACode[] statement(List locals, Tree[] trees, int start, int count){
        List codes = new ArrayList();
        for (int i = start; i < start + count; i++) {
            ACode code = statement(locals, trees[i]);
            if (code != ACode.Void) codes.add(code);
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
        for (int i = 0; i < codes.length; i++) codes[i] = expression(trees[i]);
        return codes;
    }

    /** Translates the expression. */
    private ACode expression(Tree tree) {
        if (tree == Tree.Empty) {
            return make.Void;
        } else if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef) tree;
            Symbol[] locals = Tree.symbolOf(labelDef.params);
            return make.Label(tree, tree.symbol(), locals, expression(labelDef.rhs));
        } else if (tree instanceof Tree.Block) {
            Tree[] statements = ((Tree.Block) tree).stats;
            if (statements.length == 0) return make.Void;
            int statement_count = statements.length - 1;
            List locals = new ArrayList();
            ACode[] codes = statement(locals,statements,0, statement_count);
            ACode value = expression(statements[statement_count]);
            if (locals.size() == 0 && codes.length == 0) return value;
            Symbol[] symbols =
                (Symbol[])locals.toArray(new Symbol[locals.size()]);
            return make.Block(tree, symbols, codes, value);
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
            for (int i = 0; i < tagss.length; i++)
                tagss[i] = new int[] {switchTree.tags[i]};
            ACode[] codes = new ACode[switchTree.bodies.length + 1];
            for (int i = 0; i < switchTree.bodies.length; i++)
                codes[i] = expression(switchTree.bodies[i]);
            codes[switchTree.tags.length] = expression(switchTree.otherwise);
            return make.Switch(tree, expression(switchTree.test), tagss, codes);
        } else if (tree instanceof Tree.Return) {
            return make.Return(tree, tree.symbol(), expression(((Tree.Return) tree).expr));
        } else if (tree instanceof Tree.Throw) {
            return make.Throw(tree, expression(((Tree.Throw) tree).expr));
        } else if (tree instanceof Tree.New) {
            Tree[] bases = ((Tree.New) tree).templ.parents;
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
        } else if (tree instanceof Tree.Select) {
            return make.Load(tree, location(tree));
        } else if (tree instanceof Tree.Ident) {
            if (tree.symbol() == definitions.NULL)
                return make.Constant(tree, make.NULL);
            if (tree.symbol() == definitions.ZERO)
                return make.Constant(tree, make.ZERO);
            return make.Load(tree, location(tree));
        } else if (tree instanceof Tree.Literal) {
            return make.Constant(tree, constant(((Tree.Literal) tree).value));
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    /** Translates the application. */
    private ACode apply(Tree tree, Tree fun, Tree[] targs, Tree[] vargs) {
        if (fun instanceof Tree.Ident) {
            return make.Goto(tree, tree.symbol(), expression(vargs));
        } else {
            return apply(tree, method(fun), targs, vargs);
        }
    }

    /** Translates the application. */
    private ACode apply(Tree tree, AFunction function,Tree[]targs,Tree[]vargs){
        return make.Apply(tree, function,Tree.typeOf(targs),expression(vargs));
    }

    //########################################################################
    // Private Methods - Translating functions

    /** Translates the method. */
    private AFunction method(Tree tree) {
        Symbol symbol = tree.symbol();
        if (tree instanceof Tree.Select) {
            Tree qualifier = ((Tree.Select) tree).qualifier;
            if (symbol.isJava() && symbol.owner().isModuleClass())
                return AFunction.Method(make.Void, symbol, AInvokeStyle.Static); // !!! qualifier is ignored !
            ACode object = expression(qualifier);
            return AFunction.Method(object, symbol, invokeStyle(qualifier));
        } else if (tree instanceof Tree.Ident) {
            return AFunction.Method(make.Void, symbol, AInvokeStyle.New);
        } else {
            throw Debug.abort("illegal case", tree);
        }
    }

    /** Returns the InvokeStyle to use for the qualifier. */
    private AInvokeStyle invokeStyle(Tree qualifier) {
        if (qualifier instanceof Tree.Super) {
            return AInvokeStyle.Static;
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
            Tree qualifier = ((Tree.Select) tree).qualifier;
            if (symbol.isModule())
                return ALocation.Module(symbol); // !!! qualifier is ignored !
            if (symbol.isJava() && symbol.owner().isModuleClass())
                return ALocation.Field(make.Void, symbol, true); // !!! qualifier is ignored !
            return ALocation.Field(expression(qualifier), symbol, false);
        } else if (tree instanceof Tree.Ident) {
            if (symbol.isModule()) return ALocation.Module(symbol);
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
        throw Debug.abort("illegal constant", value +" -- "+ value.getClass());
    }

    //########################################################################
}
