/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
\*                                                                      */

// $Id$

package scalac.ast.printer;

import scalac.ast.*;
import scalac.symtab.*;
import scalac.util.Debug;
import scalac.Global;
import scalac.Phase;
import scalac.Unit;
import scalac.util.Name;
import scalac.util.TypeNames;

import java.io.*;
import java.util.*;

/**
 * Text pretty printer for Scala abstract syntax trees.
 *
 * @author Michel Schinz, Matthias Zenger
 * @version 1.0
 */
public class TextTreePrinter implements TreePrinter {
    protected final PrintWriter out;

    protected int indent = 0;
    protected final int INDENT_STEP = 2;
    protected String INDENT_STRING =
        "                                        ";
    protected final int MAX_INDENT = INDENT_STRING.length();

    public TextTreePrinter(PrintWriter writer) {
        this.out = writer;
    }

    public TextTreePrinter(Writer writer) {
        this(new PrintWriter(writer));
    }

    public TextTreePrinter(OutputStream stream) {
        this(new PrintWriter(stream));
    }

    public TextTreePrinter() {
        this(System.out);
    }

    public void begin() { }

    public void end() {
        flush();
    }

    public void flush() {
        out.flush();
    }

    public TreePrinter print(String str) {
        out.print(str);
        return this;
    }

    public TreePrinter println() {
        out.println();
        return this;
    }

    public void beginSection(int level, String title) {
        out.println("[[" + title + "]]");
        flush();
    }

    protected void indent() {
        indent += Math.min(MAX_INDENT, INDENT_STEP);
    }

    protected void undent() {
        indent -= Math.max(0, INDENT_STEP);
    }

    protected void printString(String str) {
        out.print(str);
    }

    protected void printNewLine() {
        out.println();
        while (indent > INDENT_STRING.length()) {
            INDENT_STRING = INDENT_STRING + INDENT_STRING;
        }
        if (indent > 0)
            out.write(INDENT_STRING, 0, indent);
    }

    public static class SymbolUsage {
        private SymbolUsage() {
        }

        public static final SymbolUsage Definition = new SymbolUsage();
        public static final SymbolUsage Use = new SymbolUsage();
    }

    public static abstract class Text {
        private Text() {
        }

        public static final class SimpleText extends Text {
            public final String str;

            public SimpleText(String str) {
                this.str = str;
            }
        }

        public static final class LiteralText extends Text {
            public final String str;

            public LiteralText(String str) {
                this.str = str;
            }
        }

        public static final class KeywordText extends Text {
            public final String name;

            public KeywordText(String name) {
                this.name = name;
            }
        }

        public static final class IdentifierText extends Text {
            public final Symbol symbol;
            public final Name name;
            public final SymbolUsage usage;

            public IdentifierText(Symbol symbol, Name name, SymbolUsage usage) {
                this.symbol = symbol;
                this.name = name;
                this.usage = usage;
            }
        }

        public static final class SequenceText extends Text {
            public final Text[] elements;

            public SequenceText(Text[] elements) {
                this.elements = elements;
            }
        }

        private static final class NoneText extends Text {
        }

        private static final class SpaceText extends Text {
        }

        private static final class NewlineText extends Text {
        }

        public static final Text None = new NoneText();
        public static final Text Space = new SpaceText();
        public static final Text Newline = new NewlineText();

        public static Text Simple(String str) {
            return new SimpleText(str);
        }

        public static Text Literal(String str) {
            return new LiteralText(str);
        }

        public static Text Keyword(String name) {
            return new KeywordText(name);
        }

        public static Text Identifier(Symbol symbol, Name name, SymbolUsage usage) {
            return new IdentifierText(symbol, name, usage);
        }

        public static Text Sequence(Text[] elements) {
            return new SequenceText(elements);
        }
    }

    protected void print(Text text) {
        if (text == Text.None) {
            return;
        } else if (text == Text.Space) {
            printString(" ");
        } else if (text == Text.Newline) {
            printNewLine();
        } else if (text instanceof Text.SimpleText) {
            printString(((Text.SimpleText) text).str);
        } else if (text instanceof Text.LiteralText) {
            printString(((Text.LiteralText) text).str);
        } else if (text instanceof Text.KeywordText) {
            printString(((Text.KeywordText) text).name);
        } else if (text instanceof Text.IdentifierText) {
            Text.IdentifierText identifier = (Text.IdentifierText) text;
            Symbol sym = identifier.symbol;
            Name name = identifier.name;
            SymbolUsage usage = identifier.usage;
            if (sym != null) {
                if (usage == SymbolUsage.Use)
                    printString(sym.simpleName().toString());
                else
                    printString(sym.name.toString());
                if (Global.instance.uniqid)
                    printString("#" + Global.instance.uniqueID.id(sym));
            } else {
                printString(name.toString());
            }
        } else if (text instanceof Text.SequenceText) {
            print(((Text.SequenceText) text).elements);
        }
    }

    protected void print(Text[] texts) {
        for (int i = 0; i < texts.length; ++i)
            print(texts[i]);
    }

    protected static final Text KW_ABSTRACT  = Text.Keyword("abstract");
    protected static final Text KW_CASE      = Text.Keyword("case");
    protected static final Text KW_CLASS     = Text.Keyword("class");
    protected static final Text KW_DEF       = Text.Keyword("def");
    protected static final Text KW_DO        = Text.Keyword("do");
    protected static final Text KW_ELSE      = Text.Keyword("else");
    protected static final Text KW_EXTENDS   = Text.Keyword("extends");
    protected static final Text KW_FINAL     = Text.Keyword("final");
    protected static final Text KW_SEALED    = Text.Keyword("sealed");
    protected static final Text KW_FOR       = Text.Keyword("for");
    protected static final Text KW_IF        = Text.Keyword("if");
    protected static final Text KW_IMPORT    = Text.Keyword("import");
    protected static final Text KW_INTERFACE = Text.Keyword("interface");
    protected static final Text KW_OBJECT    = Text.Keyword("object");
    protected static final Text KW_NEW       = Text.Keyword("new");
    protected static final Text KW_NULL      = Text.Keyword("null");
    protected static final Text KW_OUTER     = Text.Keyword("outer");
    protected static final Text KW_OVERRIDE  = Text.Keyword("override");
    protected static final Text KW_PACKAGE   = Text.Keyword("package");
    protected static final Text KW_PRIVATE   = Text.Keyword("private");
    protected static final Text KW_PROTECTED = Text.Keyword("protected");
    protected static final Text KW_RETURN    = Text.Keyword("return");
    protected static final Text KW_STATIC    = Text.Keyword("static");
    protected static final Text KW_SUPER     = Text.Keyword("super");
    protected static final Text KW_THIS      = Text.Keyword("this");
    protected static final Text KW_TYPE      = Text.Keyword("type");
    protected static final Text KW_VAL       = Text.Keyword("val");
    protected static final Text KW_VAR       = Text.Keyword("var");
    protected static final Text KW_WITH      = Text.Keyword("with");
    protected static final Text KW_YIELD     = Text.Keyword("yield");

    protected static final Text TXT_ERROR   = Text.Simple("<error>");
    protected static final Text TXT_UNKNOWN = Text.Simple("<unknown>");
    protected static final Text TXT_NULL    = Text.Simple("<null>");
    protected static final Text TXT_OBJECT_COMMENT
        = Text.Simple("/*object*/ ");
    protected static final Text TXT_EMPTY   = Text.Simple("<empty>");

    protected static final Text TXT_QUOTE         = Text.Simple("\"");
    protected static final Text TXT_PLUS          = Text.Simple("+");
    protected static final Text TXT_COLON         = Text.Simple(":");
    protected static final Text TXT_SEMICOLON     = Text.Simple(";");
    protected static final Text TXT_DOT           = Text.Simple(".");
    protected static final Text TXT_COMMA         = Text.Simple(",");
    protected static final Text TXT_EQUAL         = Text.Simple("=");
    protected static final Text TXT_SUPERTYPE     = Text.Simple(">:");
    protected static final Text TXT_SUBTYPE       = Text.Simple("<:");
    protected static final Text TXT_HASH          = Text.Simple("#");
    protected static final Text TXT_RIGHT_ARROW   = Text.Simple("=>");
    protected static final Text TXT_LEFT_PAREN    = Text.Simple("(");
    protected static final Text TXT_RIGHT_PAREN   = Text.Simple(")");
    protected static final Text TXT_LEFT_BRACE    = Text.Simple("{");
    protected static final Text TXT_RIGHT_BRACE   = Text.Simple("}");
    protected static final Text TXT_LEFT_BRACKET  = Text.Simple("[");
    protected static final Text TXT_RIGHT_BRACKET = Text.Simple("]");
    protected static final Text TXT_BAR           = Text.Simple("|");
    protected static final Text TXT_AT            = Text.Simple("@");

    protected static final Text TXT_WITH_SP =
        Text.Sequence(new Text[]{ Text.Space, KW_WITH, Text.Space });
    protected static final Text TXT_BLOCK_BEGIN =
        Text.Sequence(new Text[]{ TXT_LEFT_BRACE, Text.Newline });
    protected static final Text TXT_BLOCK_END =
        Text.Sequence(new Text[]{ Text.Newline, TXT_RIGHT_BRACE });
    protected static final Text TXT_BLOCK_SEP =
        Text.Sequence(new Text[]{ TXT_SEMICOLON, Text.Newline });
    protected static final Text TXT_COMMA_SP =
        Text.Sequence(new Text[]{ TXT_COMMA, Text.Space });
    protected static final Text TXT_ELSE_NL =
        Text.Sequence(new Text[]{ KW_ELSE, Text.Newline });
    protected static final Text TXT_BAR_SP =
        Text.Sequence(new Text[]{ Text.Space, TXT_BAR, Text.Space });

    public void print(Global global) {
        Phase phase = global.currentPhase;
        beginSection(1, "syntax trees at "+phase+" (after "+phase.prev+")");
        for (int i = 0; i < global.units.length; i++) print(global.units[i]);
    }

    public void print(Unit unit) {
        printUnitHeader(unit);
        if (unit.body != null) {
            for (int i = 0; i < unit.body.length; ++i) {
                print(unit.body[i]);
                print(TXT_BLOCK_SEP);
            }
        } else
            print(TXT_NULL);
        printUnitFooter(unit);

        flush();
    }

    protected void printUnitHeader(Unit unit) {
        print(Text.Simple("// Scala source: " + unit.source + "\n"));
    }

    protected void printUnitFooter(Unit unit) {
        print(Text.Newline);
    }

    public TreePrinter print(Tree tree) {
        if (tree instanceof Tree.Bad) {
            print(TXT_ERROR);
        } else if (tree == Tree.Empty) {
            print(TXT_EMPTY);
        } else if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef) tree;
            printModifiers(classDef.mods);
            print((classDef.mods & Modifiers.INTERFACE) != 0 ? KW_INTERFACE : KW_CLASS);
            print(Text.Space);
            printSymbolDefinition(tree.symbol(), classDef.name);
            printParams(classDef.tparams);
            printParams(classDef.vparams);
            printOpt(TXT_COLON, classDef.tpe, false);
            printTemplate(tree.symbol(), KW_EXTENDS, classDef.impl, true);
        } else if (tree instanceof Tree.PackageDef) {
            Tree.PackageDef packageDef = (Tree.PackageDef) tree;
            print(KW_PACKAGE);
            print(Text.Space);
            print(packageDef.packaged);
            printTemplate(null, KW_WITH, packageDef.impl, true);
        } else if (tree instanceof Tree.ModuleDef) {
            Tree.ModuleDef moduleDef = (Tree.ModuleDef) tree;
            printModifiers(moduleDef.mods);
            print(KW_OBJECT);
            print(Text.Space);
            printSymbolDefinition(tree.symbol(), moduleDef.name);
            printOpt(TXT_COLON, moduleDef.tpe, false);
            printTemplate(null, KW_EXTENDS, moduleDef.impl, true);
        } else if (tree instanceof Tree.ValDef) {
            Tree.ValDef valDef = (Tree.ValDef) tree;
            printModifiers(valDef.mods);
            if ((valDef.mods & Modifiers.MUTABLE) != 0) print(KW_VAR);
            else {
                if ((valDef.mods & Modifiers.MODUL) != 0) print(TXT_OBJECT_COMMENT);
                print(KW_VAL);
            }
            print(Text.Space);
            printSymbolDefinition(tree.symbol(), valDef.name);
            printOpt(TXT_COLON, valDef.tpe, false);
            if ((valDef.mods & Modifiers.DEFERRED) == 0) {
                print(Text.Space); print(TXT_EQUAL); print(Text.Space);
                if (valDef.rhs == Tree.Empty) print("_");
                else print(valDef.rhs);
            }
        } else if (tree instanceof Tree.PatDef) {
            Tree.PatDef patDef = (Tree.PatDef) tree;
            printModifiers(patDef.mods);
            print(KW_VAL);
            print(Text.Space);
            print(patDef.pat);
            printOpt(TXT_EQUAL, patDef.rhs, true);
        } else if (tree instanceof Tree.DefDef) {
            Tree.DefDef defDef = (Tree.DefDef) tree;
            printModifiers(defDef.mods);
            print(KW_DEF);
            print(Text.Space);
            if (defDef.name.isTypeName()) print(KW_THIS);
            else printSymbolDefinition(tree.symbol(), defDef.name);
            printParams(defDef.tparams);
            printParams(defDef.vparams);
            printOpt(TXT_COLON, defDef.tpe, false);
            printOpt(TXT_EQUAL, defDef.rhs, true);
        } else if (tree instanceof Tree.AbsTypeDef) {
            Tree.AbsTypeDef absTypeDef = (Tree.AbsTypeDef) tree;
            printModifiers(absTypeDef.mods);
            print(KW_TYPE);
            print(Text.Space);
            printSymbolDefinition(tree.symbol(), absTypeDef.name);
            printBounds(absTypeDef.lobound, absTypeDef.rhs);
        } else if (tree instanceof Tree.AliasTypeDef) {
            Tree.AliasTypeDef aliasTypeDef = (Tree.AliasTypeDef) tree;
            printModifiers(aliasTypeDef.mods);
            print(KW_TYPE);
            print(Text.Space);
            printSymbolDefinition(tree.symbol(), aliasTypeDef.name);
            printParams(aliasTypeDef.tparams);
            printOpt(TXT_EQUAL, aliasTypeDef.rhs, true);
        } else if (tree instanceof Tree.Import) {
            Tree.Import importTree = (Tree.Import) tree;
            Name[] selectors = importTree.selectors;
            print(KW_IMPORT);
            print(Text.Space);
            print(importTree.expr);
            print(TXT_DOT);
            print(TXT_LEFT_BRACE);
            for (int i = 0; i < selectors.length; i = i + 2) {
                if (i > 0) print(TXT_COMMA_SP);
                print(selectors[i].toString());
                if (i + 1 < selectors.length && selectors[i] != selectors[i + 1]) {
                    print(TXT_RIGHT_ARROW);
                    print(selectors[i + 1].toString());
                }
            }
            print(TXT_RIGHT_BRACE);
        } else if (tree instanceof Tree.CaseDef) {
            Tree.CaseDef caseDef = (Tree.CaseDef) tree;
            print(KW_CASE);
            print(Text.Space);
            print(caseDef.pat);
            printOpt(KW_IF, caseDef.guard, true);
            print(Text.Space);
            print(TXT_RIGHT_ARROW);
            print(Text.Space);
            print(caseDef.body);
        } else if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef) tree;
            printSymbolDefinition(tree.symbol(), labelDef.name);
            printArray(labelDef.params, TXT_LEFT_PAREN, TXT_RIGHT_PAREN, TXT_COMMA_SP);
            print(labelDef.rhs);
        } else if (tree instanceof Tree.Block) {
            printArray(((Tree.Block) tree).stats, TXT_BLOCK_BEGIN, TXT_BLOCK_END, TXT_BLOCK_SEP);
            printType(tree);
        } else if (tree instanceof Tree.Sequence) {
            printArray(((Tree.Sequence) tree).trees, TXT_LEFT_BRACKET, TXT_RIGHT_BRACKET, TXT_COMMA_SP);
        } else if (tree instanceof Tree.Alternative) {
            printArray(((Tree.Alternative) tree).trees, TXT_LEFT_PAREN, TXT_RIGHT_PAREN, TXT_BAR_SP);
        } else if (tree instanceof Tree.Bind) {
            Tree.Bind bind = (Tree.Bind) tree;
            printSymbolDefinition(tree.symbol(), bind.name);
            print(Text.Space);
            print(TXT_AT);
            print(Text.Space);
            print(TXT_LEFT_PAREN);
            print(bind.rhs);
            print(TXT_RIGHT_PAREN);
            printType(tree);
        } else if (tree instanceof Tree.Visitor) {
            printArray(((Tree.Visitor) tree).cases, TXT_BLOCK_BEGIN, TXT_BLOCK_END, Text.Newline);
        } else if (tree instanceof Tree.Function) {
            Tree.Function function = (Tree.Function) tree;
            print(TXT_LEFT_PAREN);
            printParams(function.vparams);
            print(Text.Space);
            print(TXT_RIGHT_ARROW);
            print(Text.Space);
            print(function.body);
            print(TXT_RIGHT_PAREN);
        } else if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign) tree;
            print(assign.lhs);
            print(Text.Space);
            print(TXT_EQUAL);
            print(Text.Space);
            print(assign.rhs);
        } else if (tree instanceof Tree.If) {
            Tree.If ifTree = (Tree.If) tree;
            print(KW_IF);
            print(Text.Space);
            print(TXT_LEFT_PAREN);
            print(ifTree.cond);
            print(TXT_RIGHT_PAREN);
            indent(); print(Text.Newline);
            print(ifTree.thenp);
            undent(); print(Text.Newline);
            indent(); printOpt(TXT_ELSE_NL, ifTree.elsep, false); undent();
            printType(tree);
        } else if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch) tree;
            print("<switch>");
            print(Text.Space);
            print(TXT_LEFT_PAREN);
            print(switchTree.test);
            print(TXT_RIGHT_PAREN);
            print(Text.Space);
            indent();
            print(TXT_BLOCK_BEGIN);
            for (int i = 0; i < switchTree.tags.length; i++) {
                print(KW_CASE);
                print(Text.Space);
                print("" + switchTree.tags[i]);
                print(Text.Space);
                print(TXT_RIGHT_ARROW);
                print(Text.Space);
                print(switchTree.bodies[i]);
                print(Text.Newline);
            }
            print("<default> => ");
            print(switchTree.otherwise);
            undent();
            print(TXT_BLOCK_END);
        } else if (tree instanceof Tree.Return) {
            print(KW_RETURN);
            print(Text.Space);
            print(((Tree.Return) tree).expr);
        } else if (tree instanceof Tree.New) {
            printTemplate(null, KW_NEW, ((Tree.New) tree).templ, false);
            printType(tree);
        } else if (tree instanceof Tree.Typed) {
            Tree.Typed typed = (Tree.Typed) tree;
            print(TXT_LEFT_PAREN);
            print(typed.expr);
            print(TXT_RIGHT_PAREN);
            print(Text.Space);
            print(TXT_COLON);
            print(Text.Space);
            print(typed.tpe);
            printType(tree);
        } else if (tree instanceof Tree.TypeApply) {
            Tree.TypeApply typeApply = (Tree.TypeApply) tree;
            print(typeApply.fun);
            printArray(typeApply.args, TXT_LEFT_BRACKET, TXT_RIGHT_BRACKET, TXT_COMMA_SP);
            printType(tree);
        } else if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply) tree;
            if (apply.fun instanceof Tree.TypeTerm)
                print(apply.fun.type.resultType().symbol().fullName().toString());
            else
                print(apply.fun);
            printArray(apply.args, TXT_LEFT_PAREN, TXT_RIGHT_PAREN, TXT_COMMA_SP);
            printType(tree);
        } else if (tree instanceof Tree.Super) {
            Tree.Super superTree = (Tree.Super) tree;
            if (superTree.qualifier != TypeNames.EMPTY) {
                printSymbolUse(tree.symbol(), superTree.qualifier);
                print(TXT_DOT);
            }
            print(KW_SUPER);
            if (superTree.mixin != TypeNames.EMPTY) {
                print(TXT_LEFT_PAREN);
                print(superTree.mixin.toString());
                print(TXT_RIGHT_PAREN);
            }
            printType(tree);
        } else if (tree instanceof Tree.This) {
            Tree.This thisTree = (Tree.This) tree;
            if (thisTree.qualifier != TypeNames.EMPTY) {
                printSymbolUse(tree.symbol(), thisTree.qualifier);
                print(TXT_DOT);
            }
            print(KW_THIS);
            printType(tree);
        } else if (tree instanceof Tree.Select) {
            Tree.Select select = (Tree.Select) tree;
            print(select.qualifier);
            print(TXT_DOT);
            printSymbolUse(tree.symbol(), select.selector);
            printType(tree);
        } else if (tree instanceof Tree.Ident) {
            printSymbolUse(tree.symbol(), ((Tree.Ident) tree).name);
            printType(tree);
        } else if (tree instanceof Tree.Literal) {
            Object obj = ((Tree.Literal) tree).value;
            String str;
            if (obj instanceof String)
                str = "\"" + obj + "\"";
            else if (obj instanceof Character)
                str = "\'" + obj + "\'";
            else
                str = String.valueOf(obj);
            print(Text.Literal(str));
            printType(tree);
        } else if (tree instanceof Tree.TypeTerm) {
            print(tree.type.toString());
        } else if (tree instanceof Tree.SingletonType) {
            print(((Tree.SingletonType) tree).ref);
            print(TXT_DOT); print(KW_TYPE);
        } else if (tree instanceof Tree.SelectFromType) {
            Tree.SelectFromType selectFromType = (Tree.SelectFromType) tree;
            print(selectFromType.qualifier);
            print(Text.Space); print(TXT_HASH); print(Text.Space);
            printSymbolUse(tree.symbol(), selectFromType.selector);
        } else if (tree instanceof Tree.FunType) {
            Tree.FunType funType = (Tree.FunType) tree;
            printArray(funType.argtpes, TXT_LEFT_PAREN, TXT_RIGHT_PAREN, TXT_COMMA_SP);
            print(TXT_RIGHT_ARROW);
            print(funType.restpe);
        } else if (tree instanceof Tree.CompoundType) {
            Tree.CompoundType compoundType = (Tree.CompoundType) tree;
            printArray(compoundType.parents, Text.None, Text.None, TXT_WITH_SP);
            printArray(compoundType.refinements, TXT_BLOCK_BEGIN, TXT_BLOCK_END, Text.Newline);
        } else if (tree instanceof Tree.AppliedType) {
            Tree.AppliedType appliedType = (Tree.AppliedType) tree;
            print(appliedType.tpe);
            indent();
            print(TXT_LEFT_BRACKET);
            for (int i = 0; i < appliedType.args.length; ++i) {
                if (i > 0) print(TXT_COMMA_SP);
                print(appliedType.args[i]);
            }
            undent();
            print(TXT_RIGHT_BRACKET);
        } else if (tree instanceof Tree.Template) {
            Debug.abort("unexpected case: template");
        } else {
            print(TXT_UNKNOWN);
        }
        //print("{" + tree.type + "}");//DEBUG
        return this;
    }

    // Printing helpers

    protected void printArray(Tree[] trees, Text open, Text close, Text sep) {
        indent();
        print(open);
        for (int i = 0; i < trees.length; ++i) {
            if (i > 0) print(sep);
            print(trees[i]);
        }
        undent();
        print(close);
    }

    protected void printOpt(Text prefix, Tree tree, boolean spaceBefore) {
        if (tree != Tree.Empty) {
            if (spaceBefore)
                print(Text.Space);
            print(prefix);
            print(Text.Space);
            print(tree);
        }
    }

    // Printing of symbols

    protected void printSymbolDefinition(Symbol symbol, Name name) {
        print(Text.Identifier(symbol, name, SymbolUsage.Definition));
    }

    protected void printSymbolUse(Symbol symbol, Name name) {
        print(Text.Identifier(symbol, name, SymbolUsage.Use));
    }

    // Printing of trees

    protected void printType(Tree tree) {
        if (Global.instance.printtypes) {
            print(TXT_LEFT_BRACE);
            if (tree.type != null)
                print(Text.Simple(tree.type.toString()));
            else
                print(TXT_NULL);
            print(TXT_RIGHT_BRACE);
        }
    }

    protected void printModifiers(int flags) {
        if ((flags & Modifiers.ABSTRACT) != 0) {
            print(KW_ABSTRACT);
            print(Text.Space);
        }
        if ((flags & Modifiers.FINAL) != 0) {
            print(KW_FINAL);
            print(Text.Space);
        }
        if ((flags & Modifiers.SEALED) != 0) {
            print(KW_SEALED);
            print(Text.Space);
        }
        if ((flags & Modifiers.PRIVATE) != 0) {
            print(KW_PRIVATE);
            print(Text.Space);
        }
        if ((flags & Modifiers.PROTECTED) != 0) {
            print(KW_PROTECTED);
            print(Text.Space);
        }
        if ((flags & Modifiers.OVERRIDE) != 0) {
            print(KW_OVERRIDE);
            print(Text.Space);
        }
        if ((flags & Modifiers.CASE) != 0) {
            print(KW_CASE);
            print(Text.Space);
        }
        if ((flags & Modifiers.DEF) != 0) {
            print(KW_DEF);
            print(Text.Space);
        }
        if ((flags & Modifiers.STATIC) != 0) {
            print(KW_STATIC);
            print(Text.Space);
        }
    }

    protected void printTemplate(Symbol symbol,
                                 Text prefix,
                                 Tree.Template templ,
                                 boolean spaceBefore) {
        if (! (templ.parents.length == 0
               || (templ.parents.length == 1
                   && templ.parents[0] == Tree.Empty))) {
            if (spaceBefore)
                print(Text.Space);
            print(prefix);
            print(Text.Space);
            printArray(templ.parents, Text.None, Text.None, TXT_WITH_SP);
        }

        List types = new ArrayList();
        if (symbol != null) {
            Global global = Global.instance;
            if (global.currentPhase.id > global.PHASE.EXPLICITOUTER.id()) {
                Scope.SymbolIterator i = symbol.members().iterator(true);
                while (i.hasNext()) {
                    Symbol member = i.next();
                    if (member.isTypeAlias() || member.isAbstractType())
                        types.add(member);
                }
            }
        }
        if (templ.body.length > 0 || types.size() > 0) {
            print(Text.Space);
            indent();
            print(TXT_BLOCK_BEGIN);
            if (types.size() > 0) {
                SymbolTablePrinter printer = new SymbolTablePrinter(
                    INDENT_STRING.substring(0, indent));
                printer.indent();
                for (int i = 0; i < types.size(); ++i) {
                    if (i > 0) printer.line();
                    Symbol type = (Symbol)types.get(i);
                    printer.printSignature(type).print(';');
                }
                printer.undent();
                print(printer.toString().substring(indent));
                print(Text.Newline);
            }
            for (int i = 0; i < templ.body.length; ++i) {
                if (i > 0) print(TXT_BLOCK_SEP);
                print(templ.body[i]);
            }
            undent();
            print(TXT_BLOCK_END);
        }
    }

    protected void printParams(Tree.AbsTypeDef[] tparams) {
        if (tparams.length > 0) {
            print(TXT_LEFT_BRACKET);
            for (int i = 0; i < tparams.length; i++) {
                if (i > 0) print(TXT_COMMA_SP);
                printParam(tparams[i]);
            }
            print(TXT_RIGHT_BRACKET);
        }
    }

    protected void printParams(Tree.ValDef[][] vparamss) {
        for (int i = 0; i < vparamss.length; ++i)
            printParams(vparamss[i]);
    }

    protected void printParams(Tree.ValDef[] vparams) {
        print(TXT_LEFT_PAREN);
        for (int i = 0; i < vparams.length; ++i) {
            if (i > 0) print(TXT_COMMA_SP);
            printParam(vparams[i]);
        }
        print(TXT_RIGHT_PAREN);
    }

    protected void printParam(Tree tree) {
        if (tree instanceof Tree.AbsTypeDef) {
            Tree.AbsTypeDef absTypeDef = (Tree.AbsTypeDef) tree;
            printModifiers(absTypeDef.mods);
            printSymbolDefinition(tree.symbol(), absTypeDef.name);
            printBounds(absTypeDef.lobound, absTypeDef.rhs);
        } else if (tree instanceof Tree.ValDef && ((Tree.ValDef) tree).rhs == Tree.Empty) {
            Tree.ValDef valDef = (Tree.ValDef) tree;
            printModifiers(valDef.mods);
            printSymbolDefinition(tree.symbol(), valDef.name);
            printOpt(TXT_COLON, valDef.tpe, false);
        } else {
            Debug.abort("bad parameter: " + tree);
        }
    }

    protected void printBounds(Tree lobound, Tree hibound) {
        Definitions definitions = Global.instance.definitions;
        boolean printLoBound = lobound.type != null
            ? lobound.type().symbol() != definitions.ALL_CLASS
            : !"scala.All".equals(lobound.toString());
        if (printLoBound) printOpt(TXT_SUPERTYPE, lobound, true);
        boolean printHiBound = hibound.type != null
            ? hibound.type().symbol() != definitions.ANY_CLASS
            : !"scala.Any".equals(hibound.toString());
        if (printHiBound) printOpt(TXT_SUBTYPE, hibound, true);
    }

}
