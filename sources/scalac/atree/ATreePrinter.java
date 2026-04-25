/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import ch.epfl.lamp.util.CodePrinter;

import scalac.Global;
import scalac.Phase;
import scalac.CompilationUnit;
import scalac.symtab.Type;
import scalac.symtab.Symbol;
import scalac.symtab.SymbolTablePrinter;
import scalac.util.Debug;
import scalac.util.SourceRepresentation;

/** This class provides methods to print attributed trees. */
public class ATreePrinter {

    //########################################################################
    // Private Fields

    /** The global environment */
    private final Global global;

    /** The underlying code printer */
    private final CodePrinter printer;

    /** The underlying symbol table printer */
    private final SymbolTablePrinter symtab;

    //########################################################################
    // Public Constructors

    /** Initalizes this instance */
    public ATreePrinter() {
        this(new CodePrinter());
    }

    /** Initalizes this instance */
    public ATreePrinter(String step) {
        this(Global.instance, new CodePrinter(step));
    }

    /** Initalizes this instance */
    public ATreePrinter(CodePrinter printer) {
        this(Global.instance, printer);
    }

    /** Initalizes this instance */
    public ATreePrinter(Global global, CodePrinter printer) {
        this.global = global;
        this.printer = printer;
        this.symtab = new SymbolTablePrinter(global, printer);
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
    public ATreePrinter indent() {
        printer.indent();
        return this;
    }

    /** Decreases the indentation level by one. */
    public ATreePrinter undent() {
        printer.undent();
        return this;
    }

    /** Inserts a new line. */
    public ATreePrinter line() {
        printer.line();
        return this;
    }

    /** Inserts a white space. */
    public ATreePrinter space() {
        printer.space();
        return this;
    }

    /** Prints an opening brace followed by a new line. */
    public ATreePrinter lbrace() {
        return space().println('{').indent();
    }

    /** Prints a closing brace followed by a new line. */
    public ATreePrinter rbrace() {
        return undent().space().println('}');
    }

    //########################################################################
    // Public Methods - Printing simple values

    /** Prints a new line. */
    public ATreePrinter println() {
        printer.println();
        return this;
    }

    /** Prints the boolean value followed by a new line. */
    public ATreePrinter println(boolean value) {
        printer.println(value);
        return this;
    }

    /** Prints the byte value followed by a new line. */
    public ATreePrinter println(byte value) {
        printer.println(value);
        return this;
    }

    /** Prints the short value followed by a new line. */
    public ATreePrinter println(short value) {
        printer.println(value);
        return this;
    }

    /** Prints the char value followed by a new line. */
    public ATreePrinter println(char value) {
        printer.println(value);
        return this;
    }

    /** Prints the int value followed by a new line. */
    public ATreePrinter println(int value) {
        printer.println(value);
        return this;
    }

    /** Prints the long value followed by a new line. */
    public ATreePrinter println(long value) {
        printer.println(value);
        return this;
    }

    /** Prints the float value followed by a new line. */
    public ATreePrinter println(float value) {
        printer.println(value);
        return this;
    }

    /** Prints the double value followed by a new line. */
    public ATreePrinter println(double value) {
        printer.println(value);
        return this;
    }

    /** Prints the string followed by a new line. */
    public ATreePrinter println(String value) {
        printer.println(value);
        return this;
    }

    /** Prints the boolean value. */
    public ATreePrinter print(boolean value) {
        printer.print(value);
        return this;
    }

    /** Prints the byte value. */
    public ATreePrinter print(byte value) {
        printer.print(value);
        return this;
    }

    /** Prints the short value. */
    public ATreePrinter print(short value) {
        printer.print(value);
        return this;
    }

    /** Prints the char value. */
    public ATreePrinter print(char value) {
        printer.print(value);
        return this;
    }

    /** Prints the int value. */
    public ATreePrinter print(int value) {
        printer.print(value);
        return this;
    }

    /** Prints the long value. */
    public ATreePrinter print(long value) {
        printer.print(value);
        return this;
    }

    /** Prints the float value. */
    public ATreePrinter print(float value) {
        printer.print(value);
        return this;
    }

    /** Prints the long value. */
    public ATreePrinter print(double value) {
        printer.print(value);
        return this;
    }

    /** Prints the string. */
    public ATreePrinter print(String value) {
        printer.print(value);
        return this;
    }

    //########################################################################
    // Public Methods - Printing types and symbols

    /** Prints the symbol. */
    public ATreePrinter printSymbol(Symbol symbol) {
        symtab.printSymbolName(symbol);
        return this;
    }

    /** Prints the type. */
    public ATreePrinter printType(Type type) {
        symtab.printType(type);
        return this;
    }

    //########################################################################
    // Public Methods - Printing trees

    /** Prints all global units. */
    public ATreePrinter printGlobal(Global global) {
        Phase phase = global.currentPhase;
        println("[[attributed trees at "+phase+" (after "+phase.prev+")]]");
        return printUnits(global.units);
    }

    /** Prints the units. */
    public ATreePrinter printUnits(CompilationUnit[] units) {
        for (int i = 0; i < units.length; i++) printUnit(units[i]);
        return this;
    }

    /** Prints the unit. */
    public ATreePrinter printUnit(CompilationUnit unit) {
        println("// Scala source: " + unit.source);
        return printRepository(unit.repository);
    }

    /** Prints the repository. */
    public ATreePrinter printRepository(ARepository repository) {
        AClass[] classes = repository.classes();
        for (int i = 0; i < classes.length; i++) printClass(classes[i]);
        return this;
    }

    /** Prints the class. */
    public ATreePrinter printClass(AClass clasz) {
        printClassModifiers(clasz);
        print(clasz.isInterface() ? "interface" : "class").space();
        printSymbol(clasz.symbol());
        Symbol[] tparams = clasz.tparams();
        if (tparams.length != 0) symtab.printTypeParams(tparams);
        Symbol[] vparams = clasz.vparams();
        if (vparams.length != 0) symtab.printValueParams(vparams);
        if (clasz.symbol().typeOfThis() != clasz.symbol().thisType())
            space().print(':').printType(clasz.symbol().typeOfThis());
        space().print("extends").space();
        symtab.printTypes(clasz.parents()," with ");
        lbrace();
        printRepository(clasz);
        AField[] fields = clasz.fields();
        for (int i = 0; i < fields.length; i++) printField(fields[i]);
        AMethod[] methods = clasz.methods();
        for (int i = 0; i < methods.length; i++) printMethod(methods[i]);
        return rbrace();
    }

    /** Prints the class modifiers. */
    public ATreePrinter printClassModifiers(AClass clasz) {
        if (clasz.isDeprecated()) print("deprecated").space();
        if (clasz.isSynthetic()) print("synthetic").space();
        if (clasz.isPublic()) print("public").space();
        if (clasz.isPrivate()) print("private").space();
        if (clasz.isProtected()) print("protected").space();
        if (clasz.isFinal()) print("final").space();
        if (clasz.isAbstract()) print("abstract").space();
        return this;
    }

    /** Prints the member modifiers. */
    public ATreePrinter printMemberModifiers(AMember member) {
        if (member.isDeprecated()) print("deprecated").space();
        if (member.isSynthetic()) print("synthetic").space();
        if (member.isPublic()) print("public").space();
        if (member.isPrivate()) print("private").space();
        if (member.isProtected()) print("protected").space();
        if (member.isStatic()) print("static").space();
        return this;
    }

    /** Prints the member code. */
    public ATreePrinter printMemberCode(AMember member) {
        if (member.code() == ACode.Void) return this;
        return print('=').space().printCode(member.code());
    }

    /** Prints the field. */
    public ATreePrinter printField(AField field) {
        printFieldModifiers(field);
        symtab.printSignature(field.symbol()).space();
        return printMemberCode(field).line();
    }

    /** Prints the field modifiers. */
    public ATreePrinter printFieldModifiers(AField field) {
        printMemberModifiers(field);
        if (field.isFinal()) print("final").space();
        if (field.isVolatile()) print("volatile").space();
        if (field.isTransient()) print("transient").space();
        return this;
    }

    /** Prints the method. */
    public ATreePrinter printMethod(AMethod method) {
        printMethodModifiers(method);
        symtab.printSignature(method.symbol()).space();
        return printMemberCode(method).line();
    }

    /** Prints the method modifiers. */
    public ATreePrinter printMethodModifiers(AMethod method) {
        printMemberModifiers(method);
        if (method.isFinal()) print("final").space();
        if (method.isSynchronized()) print("synchronized").space();
        if (method.isNative()) print("native").space();
        if (method.isAbstract()) print("abstract").space();
        return this;
    }

    /** Prints the code. */
    public ATreePrinter printCode(ACode code) {
        if (code == ACode.Void) {
            return print("<void>");
        } else if (code instanceof ACode.This) {
            return printSymbol(((ACode.This) code).clasz).print('.').print("this");
        } else if (code instanceof ACode.Constant) {
            return printConstant(((ACode.Constant) code).constant);
        } else if (code instanceof ACode.Load) {
            return printLocation(((ACode.Load) code).location);
        } else if (code instanceof ACode.Store) {
            ACode.Store store = (ACode.Store) code;
            printLocation(store.location).space().print('=').space();
            return printCode(store.value);
        } else if (code instanceof ACode.Apply) {
            ACode.Apply apply = (ACode.Apply) code;
            printFunction(apply.function);
            if (apply.targs.length > 0){
                print('[');
                for (int i = 0; i < apply.targs.length; i++)
                    (i == 0 ? this : print(',').space()).printType(apply.targs[i]);
                print(']');
            }
            print('(');
            for (int i = 0; i < apply.vargs.length; i++)
                (i == 0 ? this : print(',').space()).printCode(apply.vargs[i]);
            print(')');
            return this;
        } else if (code instanceof ACode.IsAs) {
            ACode.IsAs isAs = (ACode.IsAs) code;
            printCode(isAs.value).print('.').print(isAs.cast ? "as" : "is");
            return print('[').printType(isAs.type).print(']');
        } else if (code instanceof ACode.If) {
            ACode.If ifCode = (ACode.If) code;
            print("if").space().print('(').printCode(ifCode.test).print(')').lbrace();
            printCode(ifCode.success).line();
            rbrace().space().print("else").space().lbrace();
            printCode(ifCode.failure).line();
            return rbrace();
        } else if (code instanceof ACode.Switch) {
            ACode.Switch switchCode = (ACode.Switch) code;
            print("switch").space().print('(').printCode(switchCode.test).print(')');
            lbrace();
            for (int i = 0; i < switchCode.tags.length; i++) {
                for (int j = 0; j < switchCode.tags[i].length; j++)
                    print("case").space().print(switchCode.tags[i][j]).print(':').line();
                indent().printCode(switchCode.bodies[i]).undent().line();
            }
            print("case").space().print('_').print(':').line();
            indent().printCode(switchCode.bodies[switchCode.tags.length]).undent();
            return rbrace();
        } else if (code instanceof ACode.Synchronized) {
            ACode.Synchronized sync = (ACode.Synchronized) code;
            print("synchronized").space();
            print('(').printCode(sync.lock).print(')');
            return lbrace().printCode(sync.value).rbrace();
        } else if (code instanceof ACode.Block) {
            ACode.Block block = (ACode.Block) code;
            lbrace();
            for (int i = 0; i < block.locals.length; i++) {
                print("var").space().printSymbol(block.locals[i]);
                print(":").space().printType(block.locals[i].type());
                println(";");
            }
            for (int i = 0; i < block.statements.length; i++)
                printCode(block.statements[i]).println(';');
            return printCode(block.value).line().rbrace();
        } else if (code instanceof ACode.Label) {
            ACode.Label label = (ACode.Label) code;
            print("label").space().printSymbol(label.label).print('(');
            for (int i = 0; i < label.locals.length; i++)
                (i == 0 ? this : print(',').space()).printSymbol(label.locals[i]);
            print(')').space().print('=').lbrace();
            return printCode(label.value).rbrace();
        } else if (code instanceof ACode.Goto) {
            ACode.Goto gotoCode = (ACode.Goto) code;
            print("goto").space().printSymbol(gotoCode.label).print('(');
            for (int i = 0; i < gotoCode.vargs.length; i++)
                (i == 0 ? this : print(',').space()).printCode(gotoCode.vargs[i]);
            return print(')');
        } else if (code instanceof ACode.Return) {
            ACode.Return returnCode = (ACode.Return) code;
            print("return").symtab.printSymbolUniqueId(returnCode.function).space();
            return printCode(returnCode.value);
        } else if (code instanceof ACode.Throw) {
            return print("throw").space().printCode(((ACode.Throw) code).value);
        } else if (code instanceof ACode.Drop) {
            ACode.Drop drop = (ACode.Drop) code;
            print("drop").print('[').printType(drop.type).print(']').space();
            return printCode(drop.value);
        } else {
            throw Debug.abort("unknown case", code);
        }
    }

    /** Prints the location. */
    public ATreePrinter printLocation(ALocation location) {
        if (location instanceof ALocation.Module) {
            return printSymbol(((ALocation.Module) location).module);
        } else if (location instanceof ALocation.Field) {
            ALocation.Field field = (ALocation.Field) location;
            if (field.object == ACode.Void && field.isStatic) {
                return printSymbol(field.field.owner()).print('.').printSymbol(field.field);
            }
            printCode(field.object).print('.');
            if (field.isStatic) print("<static>").space();
            return printSymbol(field.field);
        } else if (location instanceof ALocation.Local) {
            return printSymbol(((ALocation.Local) location).local);
        } else if (location instanceof ALocation.ArrayItem) {
            ALocation.ArrayItem arrayItem = (ALocation.ArrayItem) location;
            return printCode(arrayItem.array).print('(').printCode(arrayItem.index).print(')');
        } else {
            throw Debug.abort("unknown case", location);
        }
    }

    /** Prints the function. */
    public ATreePrinter printFunction(AFunction function) {
        if (function instanceof AFunction.Method) {
            AFunction.Method method = (AFunction.Method) function;
            if (method.object == ACode.Void && method.style == AInvokeStyle.New) {
                return print("new").space().printSymbol(method.method);
            } else if (method.object == ACode.Void && method.style == AInvokeStyle.Static(false)) {
                return printSymbol(method.method.owner()).print('.').printSymbol(method.method);
            } else if (method.object instanceof ACode.This && method.style == AInvokeStyle.Static(true)) {
                printSymbol(((ACode.This) method.object).clasz).print('.').print("super").print('.');
                return printSymbol(method.method);
            }
            printCode(method.object).print('.');
            if (method.style != AInvokeStyle.Dynamic) print("<" + method.style + ">").space();
            return printSymbol(method.method);
        } else if (function instanceof AFunction.Primitive) {
            return printPrimitive(((AFunction.Primitive) function).primitive);
        } else if (function instanceof AFunction.NewArray) {
            return print("new").space().printType(((AFunction.NewArray) function).element).print("[]");
        } else {
            throw Debug.abort("unknown case", function);
        }
    }

    /** Prints the primitive. */
    public ATreePrinter printPrimitive(APrimitive primitive) {
        if (primitive instanceof APrimitive.Negation) {
            return printPrimitiveOp("NEG", ((APrimitive.Negation) primitive).kind);
        } else if (primitive instanceof APrimitive.Test) {
            APrimitive.Test test = (APrimitive.Test) primitive;
            return printPrimitiveOp(test.op.toString() + (test.zero ? "Z" : ""), test.kind);
        } else if (primitive instanceof APrimitive.Comparison) {
            APrimitive.Comparison comparison = (APrimitive.Comparison) primitive;
            return printPrimitiveOp(comparison.op.toString(), comparison.kind);
        } else if (primitive instanceof APrimitive.Arithmetic) {
            APrimitive.Arithmetic arithmetic = (APrimitive.Arithmetic) primitive;
            return printPrimitiveOp(arithmetic.op.toString(), arithmetic.kind);
        } else if (primitive instanceof APrimitive.Logical) {
            APrimitive.Logical logical = (APrimitive.Logical) primitive;
            return printPrimitiveOp(logical.op.toString(), logical.kind);
        } else if (primitive instanceof APrimitive.Shift) {
            APrimitive.Shift shift = (APrimitive.Shift) primitive;
            return printPrimitiveOp(shift.op.toString(), shift.kind);
        } else if (primitive instanceof APrimitive.Conversion) {
            APrimitive.Conversion conversion = (APrimitive.Conversion) primitive;
            return printPrimitiveOp("CONV", conversion.src, conversion.dst);
        } else if (primitive instanceof APrimitive.ArrayLength) {
            return printPrimitiveOp("LENGTH", ((APrimitive.ArrayLength) primitive).kind);
        } else if (primitive instanceof APrimitive.StringConcat) {
            APrimitive.StringConcat concat = (APrimitive.StringConcat) primitive;
            return printPrimitiveOp("CONCAT", concat.lf, concat.rg);
        } else {
            throw Debug.abort("unknown case", primitive);
        }
    }

    /** Prints the primitive operation of given type kind. */
    public ATreePrinter printPrimitiveOp(String op, ATypeKind kind) {
        return printPrimitiveOp(op, kind, null);
    }

    /** Prints the primitive operation of given types. */
    public ATreePrinter printPrimitiveOp(String op, ATypeKind k1,ATypeKind k2){
        print('<').print(op).print('>');
        if (k1 != null && global.uniqid) print('#').print(k1.toString());
        if (k2 != null && global.uniqid) print(',').print(k2.toString());
        return this;
    }

    /** Prints the constant. */
    public ATreePrinter printConstant(AConstant constant) {
        if (constant == AConstant.UNIT) {
            return print("()");
        } else if (constant instanceof AConstant.BooleanValue) {
            return print(((AConstant.BooleanValue) constant).value);
        } else if (constant instanceof AConstant.ByteValue) {
            return print(((AConstant.ByteValue) constant).value);
        } else if (constant instanceof AConstant.ShortValue) {
            return print(((AConstant.ShortValue) constant).value);
        } else if (constant instanceof AConstant.CharValue) {
            return print('\'').print(((AConstant.CharValue) constant).value).print('\'');
        } else if (constant instanceof AConstant.IntValue) {
            return print(((AConstant.IntValue) constant).value);
        } else if (constant instanceof AConstant.LongValue) {
            return print(((AConstant.LongValue) constant).value);
        } else if (constant instanceof AConstant.FloatValue) {
            return print(((AConstant.FloatValue) constant).value);
        } else if (constant instanceof AConstant.DoubleValue) {
            return print(((AConstant.DoubleValue) constant).value);
        } else if (constant instanceof AConstant.StringValue) {
            return print('\"').print(SourceRepresentation.escape(((AConstant.StringValue) constant).value)).print('\"');
        } else if (constant == AConstant.NULL) {
            return print("null");
        } else if (constant == AConstant.ZERO) {
            return print("<zero>");
        } else {
            throw Debug.abort("unknown case", constant);
        }
    }

    //########################################################################
    // Public Methods - Converting

    /** Returns the string representation of this printer. */
    public String toString() {
        return printer.toString();
    }

    //########################################################################
}
