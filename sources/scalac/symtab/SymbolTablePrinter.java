/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import java.io.StringWriter;

import ch.epfl.lamp.util.CodePrinter;

import scalac.Global;
import scalac.atree.AConstant;
import scalac.symtab.Type.Constraint;
import scalac.symtab.Scope.SymbolIterator;
import scalac.util.Name;
import scalac.util.NameTransformer;
import scalac.util.Debug;

/** This class provides methods to print symbols and types. */
public class SymbolTablePrinter {

    //########################################################################
    // Private Fields

    /** The global environment */
    private final Global global;

    /** The underlying code printer */
    private final CodePrinter printer;

    //########################################################################
    // Public Constructors

    /** Creates a new instance */
    public SymbolTablePrinter() {
        this((String)null);
    }

    /** Creates a new instance */
    public SymbolTablePrinter(String step) {
        this(Global.instance, new CodePrinter(step));
    }

    /** Creates a new instance */
    public SymbolTablePrinter(CodePrinter printer) {
        this(Global.instance, printer);
    }

    /** Creates a new instance */
    public SymbolTablePrinter(Global global, CodePrinter printer) {
        this.global = global;
        this.printer = printer;
    }

    //########################################################################
    // Public Methods - Getting & Setting

    /** Returns the underlying code printer. */
    public CodePrinter getCodePrinter() {
        return printer;
    }

    //########################################################################
    // Public Methods - Formatting

    /** Increases the indentation level by one. */
    public SymbolTablePrinter indent() {
        printer.indent();
        return this;
    }

    /** Decreases the indentation level by one. */
    public SymbolTablePrinter undent() {
        printer.undent();
        return this;
    }

    /** Inserts a new line. */
    public SymbolTablePrinter line() {
        printer.line();
        return this;
    }

    /** Inserts a white space. */
    public SymbolTablePrinter space() {
        printer.space();
        return this;
    }

    //########################################################################
    // Public Methods - Printing simple values

    /** Prints a new line. */
    public SymbolTablePrinter println() {
        printer.println();
        return this;
    }

    /** Prints the boolean value followed by a new line. */
    public SymbolTablePrinter println(boolean value) {
        printer.println(value);
        return this;
    }

    /** Prints the byte value followed by a new line. */
    public SymbolTablePrinter println(byte value) {
        printer.println(value);
        return this;
    }

    /** Prints the short value followed by a new line. */
    public SymbolTablePrinter println(short value) {
        printer.println(value);
        return this;
    }

    /** Prints the char value followed by a new line. */
    public SymbolTablePrinter println(char value) {
        printer.println(value);
        return this;
    }

    /** Prints the int value followed by a new line. */
    public SymbolTablePrinter println(int value) {
        printer.println(value);
        return this;
    }

    /** Prints the long value followed by a new line. */
    public SymbolTablePrinter println(long value) {
        printer.println(value);
        return this;
    }

    /** Prints the float value followed by a new line. */
    public SymbolTablePrinter println(float value) {
        printer.println(value);
        return this;
    }

    /** Prints the double value followed by a new line. */
    public SymbolTablePrinter println(double value) {
        printer.println(value);
        return this;
    }

    /** Prints the string followed by a new line. */
    public SymbolTablePrinter println(String value) {
        printer.println(value);
        return this;
    }

    /** Prints the boolean value. */
    public SymbolTablePrinter print(boolean value) {
        printer.print(value);
        return this;
    }

    /** Prints the byte value. */
    public SymbolTablePrinter print(byte value) {
        printer.print(value);
        return this;
    }

    /** Prints the short value. */
    public SymbolTablePrinter print(short value) {
        printer.print(value);
        return this;
    }

    /** Prints the char value. */
    public SymbolTablePrinter print(char value) {
        printer.print(value);
        return this;
    }

    /** Prints the int value. */
    public SymbolTablePrinter print(int value) {
        printer.print(value);
        return this;
    }

    /** Prints the long value. */
    public SymbolTablePrinter print(long value) {
        printer.print(value);
        return this;
    }

    /** Prints the float value. */
    public SymbolTablePrinter print(float value) {
        printer.print(value);
        return this;
    }

    /** Prints the long value. */
    public SymbolTablePrinter print(double value) {
        printer.print(value);
        return this;
    }

    /** Prints the string. */
    public SymbolTablePrinter print(String value) {
        printer.print(value);
        return this;
    }

    //########################################################################
    // Public Methods - Printing scopes

    /** Prints the members of the given scope. */
    public SymbolTablePrinter printScope(Scope scope) {
        return printScope(scope, false);
    }

    /** Prints the members of the given scope. */
    public SymbolTablePrinter printScope(Scope scope, boolean lazy) {
        boolean first = true;
        for (SymbolIterator i = scope.iterator(); i.hasNext(); ) {
            Symbol member = i.next();
            if (!mustShowMember(member)) continue;
            if (first) print("{").indent(); else print(", ");
            first = false;
            line().printSignature(member);
        }
        if (!first) line().undent().print("}"); else if (!lazy) print("{}");
        return this;
    }

    /**
     * Returns true iff the given member must be printed. The default
     * implementation always returns true.
     */
    public boolean mustShowMember(Symbol member) {
        return true;
    }

    //########################################################################
    // Public Methods - Printing symbols

    /**
     * Returns the string representation of the kind of the given
     * symbol or null if there is no such representation.
     */
    public String getSymbolKind(Symbol symbol) {
        switch (symbol.kind) {
        case Kinds.NONE:
            return null;
        case Kinds.CLASS:
            if (symbol.isPackageClass()) return "package class";
            if (symbol.isModuleClass()) return "object class";
            if (symbol.isTrait()) return "trait";
            return "class";
        case Kinds.TYPE:
        case Kinds.ALIAS:
            return "type";
        case Kinds.VAL:
            if (symbol.isVariable()) return "variable";
            if (symbol.isPackage()) return "package";
            if (symbol.isModule()) return "object";
            if (symbol.isConstructor()) return "constructor";
            if (symbol.isInitializedMethod())
                if (global.debug || (symbol.flags & Modifiers.STABLE) == 0)
                    return "method";
            return "value";
        default:
            throw Debug.abort("unknown kind " + symbol.kind);
        }
    }

    /**
     * Returns the definition keyword associated to the given symbol
     * or null if there is no such keyword.
     */
    public String getSymbolKeyword(Symbol symbol) {
        if (symbol.isParameter()) return null;
        switch (symbol.kind) {
        case Kinds.NONE:
            return null;
        case Kinds.CLASS:
            if (symbol.isTrait()) return "trait";
            return "class";
        case Kinds.TYPE:
        case Kinds.ALIAS:
            return "type";
        case Kinds.VAL:
            if (symbol.isVariable()) return "var";
            if (symbol.isPackage()) return "package";
            if (symbol.isModule()) return "object";
            if (symbol.isInitializedMethod()) return "def";
            return "val";
        default:
            throw Debug.abort("unknown kind " + symbol.kind);
        }
    }

    /** Returns the name of the given symbol. */
    public String getSymbolName(Symbol symbol) {
        String name = symbol.simpleName().toString();
        if (!global.debug) name = NameTransformer.decode(name);
        return name;
    }

    /** Returns the inner string of the given symbol. */
    public String getSymbolInnerString(Symbol symbol) {
        switch (symbol.kind) {
        case Kinds.NONE : return ":";
        case Kinds.ALIAS: return "=";
        case Kinds.CLASS: return "extends";
        case Kinds.TYPE : return "<:";
        case Kinds.VAL  : return symbol.isModule() ? "extends" : ":";
        default         : throw Debug.abort("unknown kind " + symbol.kind);
        }
    }

    /** Prints the unique identifier of the given symbol */
    public SymbolTablePrinter printSymbolUniqueId(Symbol symbol) {
        if (global.uniqid) print('#').print(global.uniqueID.id(symbol));
        return this;
    }

    /** Prints the name of the given symbol */
    public SymbolTablePrinter printSymbolName(Symbol symbol) {
        print(getSymbolName(symbol));
        return printSymbolUniqueId(symbol);
    }

    /** Prints the kind and the name of the given symbol. */
    public SymbolTablePrinter printSymbolKindAndName(Symbol symbol) {
        if (symbol.isAnonymousClass()) {
            print("<template>");
            return printSymbolUniqueId(symbol);
        } else {
            String kind = getSymbolKind(symbol);
            if (kind != null) print(kind).space();
            return printSymbolName(symbol);
        }
    }

    /** Prints the type of the given symbol with the given inner string. */
    public SymbolTablePrinter printSymbolType(Symbol symbol, String inner) {
        Type type = symbol.rawFirstInfo();
        if (!(type instanceof Type.LazyType)) type = symbol.info();
        boolean star = false;
        if ((symbol.flags & Modifiers.REPEATED) != 0 &&
            type.symbol() == global.definitions.SEQ_CLASS &&
            type.typeArgs().length == 1)
        {
            type = type.typeArgs()[0];
            star = true;
        }
        printType(type, inner);
        if (star) print("*");
        return this;
    }

    /** Prints the signature of the given symbol. */
    public SymbolTablePrinter printSignature(Symbol symbol) {
        String keyword = getSymbolKeyword(symbol);
        if (keyword != null) print(keyword).space();
        String inner = getSymbolInnerString(symbol);
        return printSymbolName(symbol)
            .printType(symbol.loBound(), ">:")
            .printSymbolType(symbol, inner);
    }


    /** Prints the given type parameter section. */
    public SymbolTablePrinter printTypeParams(Symbol[] tparams) {
        print('[');
        for (int i = 0; i < tparams.length; i++) {
            if (i > 0) print(",");
            printSignature(tparams[i]);
        }
        return print(']');
    }

    /** Prints the given value parameter section. */
    public SymbolTablePrinter printValueParams(Symbol[] vparams) {
        print('(');
        for (int i = 0; i < vparams.length; i++) {
            if (i > 0) print(",");
            if (vparams[i].isDefParameter()) print("def ");
            printSymbolType(vparams[i], null);
        }
        return print(')');
    }

    //########################################################################
    // Public Methods - Printing types

    /** Returns the type to print for the given type (transitive). */
    public Type getTypeToPrintForType(Type type) {
        while (true) {
            Type result = getTypeToPrintForType0(type);
            if (result == type) return result;
            type = result;
        }
    }

    /** Returns the type to print for the given type (non-transitive). */
    public Type getTypeToPrintForType0(Type type) {
        if (type instanceof Type.ThisType) {
            Symbol clasz = ((Type.ThisType)type).sym;
            if (global.debug || !clasz.isModuleClass()) return type;
            Type prefix = getTypeToPrintForType(clasz.owner().thisType());
            Symbol module = clasz.sourceModule();
            if (module.owner() != clasz.owner()) return type;
            if (!module.type().isObjectType()) return type;
            return Type.singleType(prefix, module);
        } else if (type instanceof Type.SingleType) {
            Symbol sym = ((Type.SingleType)type).sym;
            if (global.debug) return type;
            if (sym.isSynthetic()) return type.widen();
            return type;
        } else if (type instanceof Type.CompoundType) {
            Type[] parts = ((Type.CompoundType)type).parts;
            if (global.debug) return type;
            if (type.isFunctionType()) return parts[1];
            return type;
        } else if (type instanceof Type.TypeVar) {
            Type.Constraint constr = ((Type.TypeVar)type).constr;
            if (constr.inst != Type.NoType) return constr.inst;
            return type;
        }
        return type;
    }

    /** Prints the given types separated by infix. */
    public SymbolTablePrinter printTypes(Type[] types, String infix) {
        for (int i = 0; i < types.length; i++) {
            if (i > 0) print(infix);
            printType(types[i]);
        }
        return this;
    }

    /** Prints the given type. */
    public SymbolTablePrinter printType(Type type) {
        return printType0(getTypeToPrintForType(type));
    }
    public SymbolTablePrinter printType0(Type type) {
        printCommonPart(type);
        if (type instanceof Type.ThisType || type instanceof Type.SingleType) {
            return print(".type");
        }
        return this;
    }

    /** Prints the given type with the given inner string. */
    public SymbolTablePrinter printType(Type type, String inner) {
        if ("<:".equals(inner) && type.symbol() == global.definitions.ANY_CLASS ||
            ">:".equals(inner) && type.symbol() == global.definitions.ALL_CLASS)
            return this;
        else
            return printType0(getTypeToPrintForType(type), inner);
    }
    public SymbolTablePrinter printType0(Type type, String inner) {
        if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)type;
            if (polyType.tparams.length != 0 || global.debug) printTypeParams(polyType.tparams);
            return printType(polyType.result, inner);
        } else if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            return printValueParams(methodType.vparams).printType(methodType.result, inner);
        }
        if (inner != null) {
            if (!inner.startsWith(":")) space();
            print(inner).space();
        }
        return printType0(type);
    }

    /** Prints a function type with the given type arguments. */
    public SymbolTablePrinter printFunctionType(Type[] types) {
        Type[] args = new Type[types.length - 1];
        for (int i = 0; i < args.length; i++) args[i] = types[i];
        print('(').printTypes(args, ",").print(") => ");
        printType(types[types.length - 1]);
        return this;
    }

    /** Prints a template type with the given base types. */
    public SymbolTablePrinter printTemplateType(Type[] types) {
        print("<template: ").printTypes(types, " with ").print(" {...}>");
        return this;
    }

    /** Prints the type and prefix common part of the given type. */
    public SymbolTablePrinter printCommonPart(Type type) {
        if (type == Type.ErrorType) {
            return print("<error>");
        } else if (type == Type.AnyType) {
            return print("<any type>");
        } else if (type == Type.NoType) {
            return print("<notype>");
        } else if (type instanceof Type.ThisType) {
            Symbol sym = ((Type.ThisType)type).sym;
            if (sym == Symbol.NONE) return print("<local>.this");
            if ((sym.isAnonymousClass() || sym.isCompoundSym()) && !global.debug)
                return print("this");
            return printSymbolName(sym).print(".this");
        } else if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            Type pre = typeRef.pre;
            Symbol sym = typeRef.sym;
            Type[] args = typeRef.args;
            if (!global.debug) {
                if (type.isFunctionType())
                    return printFunctionType(args);
                if (sym.isAnonymousClass() || sym.isCompoundSym())
                    return printTemplateType(pre.memberInfo(sym).parents());
            }
            printPrefix(pre).printSymbolName(sym);
            //print("{" + sym.owner() + "}");//DEBUG
            if (args.length != 0) print('[').printTypes(args, ",").print(']');
            return this;
        } else if (type instanceof Type.SingleType) {
            Type.SingleType singleType = (Type.SingleType)type;
            return printPrefix(singleType.pre).printSymbolName(singleType.sym);
        } else if (type instanceof Type.ConstantType) {
            Type.ConstantType constantType = (Type.ConstantType)type;
            return printType(constantType.base)
                .print("(").print(constantType.value.toString()).print(")");
        } else if (type instanceof Type.CompoundType) {
            Type.CompoundType compoundType = (Type.CompoundType)type;
            return printTypes(compoundType.parts, " with ").space()
                .printScope(compoundType.members, true)
                .printSymbolUniqueId(type.symbol());
        } else if (type instanceof Type.MethodType) {
            return printType0(type, null);
        } else if (type instanceof Type.PolyType) {
            return printType0(type, null);
        } else if (type instanceof Type.OverloadedType) {
            return printTypes(((Type.OverloadedType)type).alttypes, " <and> ");
        } else if (type instanceof Type.TypeVar) {
            return printType(((Type.TypeVar)type).origin).print("?");
        } else if (type instanceof Type.UnboxedType) {
            return print(type.unboxedName(((Type.UnboxedType)type).tag).toString());
        } else if (type instanceof Type.UnboxedArrayType) {
            return printType(((Type.UnboxedArrayType)type).elemtp).print("[]");
        } else if (type instanceof Type.LazyType) {
            if (!global.debug) return print("?");
            String classname = type.getClass().getName();
            return print("<lazy type ").print(classname).print(">");
        }
        String classname = type.getClass().getName();
        return print("<unknown type ").print(classname).print(">");
    }

    //########################################################################
    // Public Methods - Printing prefixes

    /** Returns the type to print for the given prefix (transitive). */
    public Type getTypeToPrintForPrefix(Type prefix) {
        while (true) {
            Type result = getTypeToPrintForPrefix0(prefix);
            if (result == prefix || result == null) return result;
            prefix = result;
        }
    }

    /** Returns the type to print for the given prefix (non-transitive). */
    public Type getTypeToPrintForPrefix0(Type prefix) {
        if (!global.debug) {
            if (prefix.symbol().isNone()) return null;
            if (prefix.symbol().isRoot()) return null;
        }
        return getTypeToPrintForType0(prefix);
    }

    /** Prints the given type as a prefix. */
    public SymbolTablePrinter printPrefix(Type prefix) {
        prefix = getTypeToPrintForPrefix(prefix);
        return prefix == null ? this : printPrefix0(prefix);
    }
    public SymbolTablePrinter printPrefix0(Type prefix) {
        printCommonPart(prefix);
        if (prefix instanceof Type.ThisType || prefix instanceof Type.SingleType) {
            return print(".");
        }
        return print("#");
    }

    //########################################################################
    // Public Methods - Printing constants

    /** Prints the given constant value. */
    public SymbolTablePrinter printConstantValue(AConstant value) {
        return print("(").print(value.toString()).print(")");
    }

    //########################################################################
    // Public Methods - Converting

    /** Returns the string representation of this printer. */
    public String toString() {
        return printer.toString();
    }

    //########################################################################
}
