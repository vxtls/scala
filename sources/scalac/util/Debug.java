/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import scala.tools.util.debug.Debugger;
import scala.tools.util.debug.ToStringDebugger;

import scalac.Global;
import scalac.ast.Tree;
import scalac.atree.AConstant;
import scalac.symtab.Scope;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.symtab.Modifiers;
import scalac.util.Name;

/**
 * Debugging class, used e.g. to obtain string representations of
 * compiler data structures that are not "pretty-printed" and thus
 * easier to relate to the source code.
 *
 * All methods are static to be easily useable in any context.
 *
 * @author Michel Schinz
 * @version 1.0
 */
public class Debug extends scala.tools.util.debug.Debug {

    //########################################################################
    // Private Initialization

    /**
     * Forces the initialization of this class. Returns the boolean
     * value true so that it can be invoked from an assert statement.
     */
    public static boolean initialize() {
        // nothing to do, everything is done in the static initializer
        return true;
    }

    static {
        addDebugger(new ToStringDebugger(Tree.class));
        addDebugger(new ToStringDebugger(Type.class));
        addDebugger(SymbolDebugger.object);
        addDebugger(ScopeDebugger.object);
    }

    //########################################################################
    // Public Methods - Logging

    public static boolean log(Object a) {
        return logAll(new Object[] {a});
    }

    public static boolean log(Object a, Object b) {
        return logAll(new Object[] {a, b});
    }

    public static boolean log(Object a, Object b, Object c) {
        return logAll(new Object[] {a, b, c});
    }

    public static boolean log(Object a, Object b, Object c, Object d) {
        return logAll(new Object[] {a, b, c, d});
    }

    public static boolean logAll(Object[] args) {
        return Global.instance.log(showAll(args, null));
    }

    //########################################################################
    // showTree

    private static void append(StringBuffer buf, Name[] names) {
        for (int i = 0; i < names.length; i++) {
            if (i > 0) buf.append(",");
            append(buf, names[i]);
        }
    }

    private static void append(StringBuffer buf, Name name) {
        buf.append("\"" + name + '"');
    }

    private static void append(StringBuffer buf, String str) {
        buf.append("\"" + str + '"');
    }

    private static void append(StringBuffer buf, Tree[] trees) {
        append(buf, trees, false);
    }

    private static void append(StringBuffer buf, Tree[] trees, boolean showType) {
        buf.append('[');
        for (int i = 0; i < trees.length; i++) {
            if (i > 0) buf.append(',');
            append(buf, trees[i], showType);
        }
        buf.append(']');
    }

    private static void append(StringBuffer buf, Tree[][] trees) {
        for (int i = 0; i < trees.length; i++) {
            buf.append('[');
            append(buf, trees[i]);
            buf.append(']');
        }
    }

    private static void append(StringBuffer buf, Tree tree) {
        append(buf, tree, false);
    }

    private static void append(StringBuffer buf, Tree tree, boolean showType) {
        if (tree == Tree.Empty) {
            buf.append("Empty(");
        } else if (tree instanceof Tree.Attributed) {
            Tree.Attributed attributed = (Tree.Attributed)tree;
            buf.append("Attributed(");
            append(buf, attributed.attribute);
            buf.append(',');
            append(buf, attributed.definition);
        } else if (tree instanceof Tree.DocDef) {
            Tree.DocDef docDef = (Tree.DocDef)tree;
            buf.append("DocDef(");
            append(buf, docDef.comment);
            buf.append(',');
            append(buf, docDef.definition);
        } else if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef)tree;
            buf.append("ClassDef(");
            Modifiers.Helper.toString(buf, classDef.mods);
            buf.append(',');
            append(buf, classDef.name);
            buf.append(',');
            append(buf, classDef.tparams);
            buf.append(',');
            append(buf, classDef.vparams);
            buf.append(',');
            append(buf, classDef.tpe);
            buf.append(',');
            append(buf, classDef.impl);
        } else if (tree instanceof Tree.PackageDef) {
            Tree.PackageDef packageDef = (Tree.PackageDef)tree;
            buf.append("PackageDef(");
            append(buf, packageDef.packaged);
            buf.append(',');
            append(buf, packageDef.impl);
        } else if (tree instanceof Tree.ModuleDef) {
            Tree.ModuleDef moduleDef = (Tree.ModuleDef)tree;
            buf.append("ModuleDef(");
            Modifiers.Helper.toString(buf, moduleDef.mods);
            buf.append(',');
            append(buf, moduleDef.name);
            buf.append(',');
            append(buf, moduleDef.tpe);
            buf.append(',');
            append(buf, moduleDef.impl);
        } else if (tree instanceof Tree.ValDef) {
            Tree.ValDef valDef = (Tree.ValDef)tree;
            buf.append("ValDef(");
            Modifiers.Helper.toString(buf, valDef.mods);
            buf.append(',');
            append(buf, valDef.name);
            buf.append(',');
            append(buf, valDef.tpe, showType);
            buf.append(',');
            append(buf, valDef.rhs, showType);
        } else if (tree instanceof Tree.PatDef) {
            Tree.PatDef patDef = (Tree.PatDef)tree;
            buf.append("PatDef(");
            Modifiers.Helper.toString(buf, patDef.mods);
            buf.append(',');
            append(buf, patDef.pat);
            buf.append(',');
            append(buf, patDef.rhs);
        } else if (tree instanceof Tree.DefDef) {
            Tree.DefDef defDef = (Tree.DefDef)tree;
            buf.append("DefDef(");
            Modifiers.Helper.toString(buf, defDef.mods);
            buf.append(',');
            append(buf, defDef.name);
            buf.append(',');
            append(buf, defDef.tparams);
            buf.append(',');
            append(buf, defDef.vparams);
            buf.append(',');
            append(buf, defDef.tpe);
            buf.append(',');
            append(buf, defDef.rhs);
        } else if (tree instanceof Tree.AbsTypeDef) {
            Tree.AbsTypeDef absTypeDef = (Tree.AbsTypeDef)tree;
            buf.append("AbsTypeDef(");
            Modifiers.Helper.toString(buf, absTypeDef.mods);
            buf.append(',');
            append(buf, absTypeDef.name);
            buf.append(',');
            append(buf, absTypeDef.rhs);
            buf.append(',');
            append(buf, absTypeDef.lobound);
        } else if (tree instanceof Tree.AliasTypeDef) {
            Tree.AliasTypeDef aliasTypeDef = (Tree.AliasTypeDef)tree;
            buf.append("AliasTypeDef(");
            Modifiers.Helper.toString(buf, aliasTypeDef.mods);
            buf.append(',');
            append(buf, aliasTypeDef.name);
            buf.append(',');
            append(buf, aliasTypeDef.tparams);
            buf.append(',');
            append(buf, aliasTypeDef.rhs);
        } else if (tree instanceof Tree.Import) {
            Tree.Import importTree = (Tree.Import)tree;
            buf.append("Import(");
            append(buf, importTree.expr);
            buf.append(",");
            append(buf, importTree.selectors);
        } else if (tree instanceof Tree.CaseDef) {
            Tree.CaseDef caseDef = (Tree.CaseDef)tree;
            buf.append("CaseDef(");
            buf.append(",");
            append(buf, caseDef.pat);
            buf.append(",");
            append(buf, caseDef.guard);
            buf.append(",");
            append(buf, caseDef.body);
        } else if (tree instanceof Tree.Template) {
            Tree.Template template = (Tree.Template)tree;
            buf.append("Template(");
            append(buf, template.parents);
            buf.append(',');
            append(buf, template.body);
        } else if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef)tree;
            buf.append("LabelDef(");
            buf.append(",");
            append(buf, labelDef.name);
            buf.append(',');
            append(buf, labelDef.params);
            buf.append(',');
            append(buf, labelDef.rhs);
        } else if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block)tree;
            buf.append("Block(");
            append(buf, block.stats);
            buf.append(',');
            append(buf, block.expr);
        } else if (tree instanceof Tree.Sequence) {
            Tree.Sequence sequence = (Tree.Sequence)tree;
            buf.append("Sequence(");
            buf.append(',');
            append(buf, sequence.trees);
        } else if (tree instanceof Tree.Alternative) {
            Tree.Alternative alternative = (Tree.Alternative)tree;
            buf.append("Alternative(");
            buf.append(',');
            append(buf, alternative.trees);
        } else if (tree instanceof Tree.Bind) {
            Tree.Bind bind = (Tree.Bind)tree;
            buf.append("Bind(");
            append(buf, bind.name);
            buf.append(',');
            append(buf, bind.rhs);
        } else if (tree instanceof Tree.Visitor) {
            Tree.Visitor visitor = (Tree.Visitor)tree;
            buf.append("ClassDef(");
            append(buf, visitor.cases);
        } else if (tree instanceof Tree.Function) {
            Tree.Function function = (Tree.Function)tree;
            buf.append("Function(");
            append(buf, function.vparams);
            buf.append(',');
            append(buf, function.body);
        } else if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign)tree;
            buf.append("Assign(");
            append(buf, assign.lhs);
            buf.append(',');
            append(buf, assign.rhs);
        } else if (tree instanceof Tree.If) {
            Tree.If ifTree = (Tree.If)tree;
            buf.append("If(");
            append(buf, ifTree.cond);
            buf.append(',');
            append(buf, ifTree.thenp);
            buf.append(',');
            append(buf, ifTree.elsep);
        } else if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch)tree;
            buf.append("Switch(");
            buf.append(',');
            append(buf, switchTree.test);
            buf.append(',');
            buf.append(switchTree.tags);
            buf.append(',');
            append(buf, switchTree.bodies);
            buf.append(",");
            append(buf, switchTree.otherwise);
        } else if (tree instanceof Tree.Return) {
            Tree.Return returnTree = (Tree.Return)tree;
            buf.append("Return(");
            append(buf, returnTree.expr, showType);
            buf.append(')');
        } else if (tree instanceof Tree.Throw) {
            Tree.Throw throwTree = (Tree.Throw)tree;
            buf.append("Throw(");
            append(buf, throwTree.expr, showType);
        } else if (tree instanceof Tree.New) {
            Tree.New newTree = (Tree.New)tree;
            buf.append("New(");
            append(buf, newTree.init, showType);
        } else if (tree instanceof Tree.Create) {
            Tree.Create create = (Tree.Create)tree;
            buf.append("Create(");
            append(buf, create.qualifier);
            buf.append(',');
            append(buf, create.targs);
        } else if (tree instanceof Tree.Typed) {
            Tree.Typed typed = (Tree.Typed)tree;
            buf.append("Typed(");
            append(buf, typed.expr, showType);
            buf.append(",");
            append(buf, typed.tpe, showType);
        } else if (tree instanceof Tree.TypeApply) {
            Tree.TypeApply typeApply = (Tree.TypeApply)tree;
            buf.append("TypeApply(");
            append(buf, typeApply.fun, showType);
            buf.append(',');
            append(buf, typeApply.args, showType);
        } else if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            buf.append("Apply(");
            append(buf, apply.fun, showType);
            buf.append(',');
            append(buf, apply.args, showType);
        } else if (tree instanceof Tree.Super) {
            Tree.Super superTree = (Tree.Super)tree;
            buf.append("Super(");
            append(buf, superTree.qualifier);
            buf.append(',');
            append(buf, superTree.mixin);
        } else if (tree instanceof Tree.This) {
            Tree.This thisTree = (Tree.This)tree;
            buf.append("This(");
            append(buf, thisTree.qualifier);
        } else if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select)tree;
            buf.append("Select(");
            append(buf, select.qualifier, showType);
            buf.append(',');
            append(buf, select.selector);
        } else if (tree instanceof Tree.Ident) {
            Tree.Ident ident = (Tree.Ident)tree;
            buf.append("Ident(");
            append(buf, ident.name);
        } else if (tree instanceof Tree.Literal) {
            Tree.Literal literal = (Tree.Literal)tree;
            AConstant value = literal.value;
            buf.append("Literal(" + value);
        } else if (tree instanceof Tree.TypeTerm) {
            buf.append("TypeTerm(");
        } else if (tree instanceof Tree.SingletonType) {
            Tree.SingletonType singletonType = (Tree.SingletonType)tree;
            buf.append("SingletonType(");
            append(buf, singletonType.ref, showType);
        } else if (tree instanceof Tree.SelectFromType) {
            Tree.SelectFromType selectFromType = (Tree.SelectFromType)tree;
            buf.append("SelectFromType(");
            append(buf, selectFromType.qualifier, showType);
            buf.append(',');
            append(buf, selectFromType.selector);
        } else if (tree instanceof Tree.FunType) {
            Tree.FunType funType = (Tree.FunType)tree;
            buf.append("FunType(");
            append(buf, funType.argtpes);
            buf.append(',');
            append(buf, funType.restpe);
        } else if (tree instanceof Tree.CompoundType) {
            Tree.CompoundType compoundType = (Tree.CompoundType)tree;
            buf.append("CompoundType(");
            append(buf, compoundType.parents);
            buf.append(',');
            append(buf, compoundType.refinements);
        } else if (tree instanceof Tree.AppliedType) {
            Tree.AppliedType appliedType = (Tree.AppliedType)tree;
            buf.append("AppliedType(");
            append(buf, appliedType.tpe);
            buf.append(',');
            append(buf, appliedType.args);
        } else if (tree instanceof Tree.Try) {
            Tree.Try tryTree = (Tree.Try)tree;
            buf.append("Try(");
            append(buf, tryTree.block);
            buf.append(',');
            append(buf, tryTree.catcher);
            buf.append(',');
            append(buf, tryTree.finalizer);
        } else {
            buf.append(tree.getClass().getName() + "(");
        }
        buf.append(')');
        if (showType) buf.append(":" + tree.type);
    }

    public static String showTree(Tree tree, boolean showType) {
        StringBuffer buf = new StringBuffer();
        append(buf, tree, showType);
        return buf.toString();
    }

    public static String showTree(Tree tree) {
        return showTree(tree, false);
    }

    //########################################################################
    // Public Methods - Bootstrapping

    // !!! all the following methods are only needed for bootstraping
    // !!! remove them after next release (current is 1.2.0.0)

    public static Error abort() {
        return scala.tools.util.debug.Debug.abort();
    }
    public static Error abort(Throwable cause) {
        return scala.tools.util.debug.Debug.abort(cause);
    }
    public static Error abort(Object object) {
        return scala.tools.util.debug.Debug.abort(object);
    }
    public static Error abort(Object object, Throwable cause) {
        return scala.tools.util.debug.Debug.abort(object, cause);
    }
    public static Error abort(String message) {
        return scala.tools.util.debug.Debug.abort(message);
    }
    public static Error abort(String message, Throwable cause) {
        return scala.tools.util.debug.Debug.abort(message, cause);
    }
    public static Error abort(String message, Object object) {
        return scala.tools.util.debug.Debug.abort(message, object);
    }
    public static Error abort(String message, Object object, Throwable cause) {
        return scala.tools.util.debug.Debug.abort(message, object, cause);
    }

    public static Error abortIllegalCase(int value) {
        return scala.tools.util.debug.Debug.abortIllegalCase(value);
    }
    public static Error abortIllegalCase(Object object) {
        return scala.tools.util.debug.Debug.abortIllegalCase(object);
    }

    public static String show(Object a) {
        return scala.tools.util.debug.Debug.show(a);
    }
    public static String toString(Object a) {
        return show(a);
    }
    public static String show(Object a, Object b) {
        return scala.tools.util.debug.Debug.show(a, b);
    }
    public static String show(Object a, Object b, Object c) {
        return scala.tools.util.debug.Debug.show(a, b, c);
    }
    public static String show(Object a, Object b, Object c, Object d) {
        return scala.tools.util.debug.Debug.show(a, b, c, d);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g, Object h)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g, h);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g, Object h, Object i)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g, h, i);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g, Object h, Object i, Object j)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g, h, i, j);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g, Object h, Object i, Object j, Object k)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g, h, i, j, k);
    }
    public static String show(Object a, Object b, Object c, Object d, Object e,
        Object f, Object g, Object h, Object i, Object j, Object k, Object l)
    {
        return scala.tools.util.debug.Debug.show(a, b, c, d, e, f, g, h, i, j, k, l);
    }

    public static String showAll(Object[] objects) {
        return scala.tools.util.debug.Debug.showAll(objects);
    }
    public static String showAll(Object[] objects, String separator) {
        return scala.tools.util.debug.Debug.showAll(objects, separator);
    }

    public static String showTree(Tree[] trees, boolean showType) {
        StringBuffer buf = new StringBuffer();
        append(buf, trees, showType);
        return buf.toString();
    }

    public static String showTree(Tree[] trees) {
        return showTree(trees, false);
    }

    //########################################################################
}

/** This class implements a debugger for symbols. */
class SymbolDebugger implements Debugger {

    //########################################################################
    // Public Constants

    /** The unique instance of this class. */
    public static final SymbolDebugger object = new SymbolDebugger();

    //########################################################################
    // Protected Constructors

    /** Initializes this instance. */
    protected SymbolDebugger() {}

    //########################################################################
    // Public Methods

    public boolean canAppend(Object object) {
        return object instanceof Symbol;
    }

    public void append(StringBuffer buffer, Object object) {
        Symbol symbol = (Symbol)object;
        if (!symbol.isNone() && !symbol.owner().isRoot() && !symbol.isRoot()) {
            Debug.append(buffer, symbol.owner());
            buffer.append(".");
        }
        buffer.append(symbol.name);
        if (Global.instance.uniqid) {
            buffer.append('#');
            buffer.append(symbol.id);
        }
        if (symbol.isConstructor()) {
            buffer.append('(');
            buffer.append(symbol.constructorClass().name);
            buffer.append(')');
        }
    }

    //########################################################################
}

/** This class implements a debugger for scopes. */
class ScopeDebugger implements Debugger {

    //########################################################################
    // Public Constants

    /** The unique instance of this class. */
    public static final ScopeDebugger object = new ScopeDebugger();

    //########################################################################
    // Protected Constructors

    /** Initializes this instance. */
    protected ScopeDebugger() {}

    //########################################################################
    // Public Methods

    public boolean canAppend(Object object) {
        return object instanceof Scope;
    }

    public void append(StringBuffer buffer, Object object) {
        Scope scope = (Scope)object;
        buffer.append('{');
        for (Scope.SymbolIterator i = scope.iterator(); i.hasNext();) {
            Debug.append(buffer, i.next());
            if (i.hasNext()) buffer.append(',');
        }
        buffer.append('}');
    }

    //########################################################################
}
