/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.symtab.classfile;

import scala.tools.util.AbstractFile;
import scala.tools.util.AbstractFileReader;
import scala.tools.util.Position;
import scalac.*;
import scalac.util.*;
import scalac.symtab.*;
import scalac.symtab.Scope.SymbolIterator;
import java.io.*;
import java.util.*;

//todo: don't keep statics module in scope.

public class ClassfileParser implements ClassfileConstants {

    static final int CLASS_ATTR  = SOURCEFILE_ATTR
                                 | INNERCLASSES_ATTR
                                 | SYNTHETIC_ATTR
                                 | DEPRECATED_ATTR
                                 | META_ATTR
                                 | SCALA_ATTR
                                 | JACO_ATTR
                                 | SIG_ATTR;
    static final int METH_ATTR   = CODE_ATTR
                                 | EXCEPTIONS_ATTR
                                 | SYNTHETIC_ATTR
                                 | DEPRECATED_ATTR
                                 | META_ATTR
                                 | SIG_ATTR
                                 | BRIDGE_ATTR;
    static final int FIELD_ATTR  = CONSTANT_VALUE_ATTR
                                 | SYNTHETIC_ATTR
                                 | DEPRECATED_ATTR
                                 | META_ATTR
                                 | SIG_ATTR;

    protected final Global global;
    protected final AbstractFileReader in;
    protected final Symbol c;
    protected final Symbol m;
    protected final Type ctype;
    protected final JavaTypeFactory make;
    protected final ConstantPool pool;
    protected final AttributeParser attrib;
    protected final Scope locals;
    protected final Scope statics;


    private ClassfileParser(Global global, AbstractFileReader in, Symbol c, JavaTypeFactory make, ConstantPool pool) {
        this.global = global;
        this.in = in;
        this.c = c;
        this.m = c.linkedModule();
        this.ctype = make.classType(c);
        this.make = make;
        this.pool = pool;
        this.attrib = new AttributeParser(in, pool, this);
        this.locals = new Scope();
        this.statics = new Scope();
    }


    /** parse the classfile and throw IO exception if there is an
     *  error in the classfile structure
     */
    public static void parse(Global global, AbstractFile file, Symbol c) throws IOException {
        AbstractFileReader in = new AbstractFileReader(file);
        try {
            int magic = in.nextInt();
            if (magic != JAVA_MAGIC)
                throw new IOException("class file '" + in.file + "' "
                    + "has wrong magic number 0x" + Integer.toHexString(magic)
                    + ", should be 0x" + Integer.toHexString(JAVA_MAGIC));
            int minorVersion = in.nextChar();
            int majorVersion = in.nextChar();
            if ((majorVersion < JAVA_MAJOR_VERSION) ||
                ((majorVersion == JAVA_MAJOR_VERSION) &&
                 (minorVersion < JAVA_MINOR_VERSION)))
                throw new IOException("class file '" + in.file + "' "
                    + "has unknown version "
                    + majorVersion + "." + minorVersion
                    + ", should be less than "
                    + JAVA_MAJOR_VERSION + "." + JAVA_MINOR_VERSION);
            JavaTypeFactory make = new JavaTypeCreator(global.definitions);
            Signatures sigs = new Signatures(global, make, in);
            ConstantPool pool = new ConstantPool(in, sigs);
            int flags = in.nextChar();
            Symbol clasz = pool.getClass(in.nextChar());
            if (c != clasz)
                throw new IOException("class file '" + in.file + "' "
                    + "contains wrong class " + clasz.staticType());
            new ClassfileParser(global, in, c, make, pool).parse(flags);
        } catch (RuntimeException e) {
            if (global.debug) e.printStackTrace();
            throw new IOException("class file '" + in.file + "' is broken");
        }
    }

    protected void parse(int flags) {
        {
            // todo: correct flag transition
            c.flags = transFlags(flags);
            if (shouldTreatAsJavaCaseClass(c))
                c.flags |= Modifiers.CASE | Modifiers.JAVA;
            if ((c.flags & Modifiers.DEFERRED) != 0)
                c.flags = c.flags & ~Modifiers.DEFERRED | Modifiers.ABSTRACT;
            Type supertpe = readClassType(in.nextChar());
            Type[] basetpes = new Type[in.nextChar() + 1];
            // set info of class
            Type classInfo = Type.compoundType(basetpes, locals, c);
            c.setInfo(classInfo);
            // set info of statics class
            Symbol staticsClass = m.moduleClass();
            assert staticsClass.isModuleClass(): Debug.show(staticsClass);
            Type staticsInfo = Type.compoundType(Type.EMPTY_ARRAY, statics, staticsClass);
            staticsClass.setInfo(staticsInfo);
            m.setInfo(make.classType(staticsClass));
            basetpes[0] = supertpe;
            for (int i = 1; i < basetpes.length; i++)
                basetpes[i] = readClassType(in.nextChar());
            int fieldCount = in.nextChar();
            for (int i = 0; i < fieldCount; i++)
                parseField();
            int methodCount = in.nextChar();
            for (int i = 0; i < methodCount; i++)
                parseMethod();

            Symbol constr = c.primaryConstructor();
            if (!constr.isInitialized()) {
                constr.setInfo(
                    Type.MethodType(Symbol.EMPTY_ARRAY, ctype));
                if ((c.flags & Modifiers.INTERFACE) == 0)
                    constr.flags |= Modifiers.PRIVATE;
            }
            attrib.readAttributes(c, classInfo, CLASS_ATTR);
            patchFoundationClassInfo();
            //System.out.println("dynamic class: " + c);
            //System.out.println("statics class: " + staticsClass);
            //System.out.println("module: " + m);
            //System.out.println("modules class: " + m.type().symbol());

            int savedFlags = c.flags;
            c.flags |= Modifiers.INITIALIZED;
            // hack to make memberType in addInheritedOverloaded work
            if (global.currentPhase.id <= global.PHASE.REFCHECK.id() &&
                !c.name.toString().endsWith("$class"))
                addInheritedOverloaded();

            //if (global.debug) {
            //    Symbol[] elems = c.members().elements();
            //    global.log(c + " defines: ");
            //    for (int i = 0; i < elems.length; i++) {
            //        global.log(elems[i] + ":" + elems[i].type());
            //    }
            //}

            c.flags = savedFlags;

            // Add static members of superclass
	    // todo: remove
            Symbol superclass = supertpe.symbol();
            if (m.isJava() && superclass.isJava()) {
                Symbol mclass = m.moduleClass();
                SymbolIterator i = superclass.linkedModule().moduleClass()
                    .members().iterator();
                outer:
                while (i.hasNext()) {
                    Symbol member = i.next();
                    Symbol current = statics.lookup(member.name);
                    if (!current.isNone()) {
                        if (!member.isTerm()) continue outer;
                        Type info = member.info();
                        Symbol[] currents = current.alternativeSymbols();
                        inner:
                        for (int j = 0; j < currents.length; j++) {
                            if (currents[j].owner() != mclass)
                                continue inner;
                            if (currents[j].info().isSubType(info))
                                continue outer;
                        }
                    }
                    statics.enterOrOverload(member);
                }
            }
        }
    }

    private void addInheritedOverloaded() {
        Symbol[] elems = c.members().elements();
        for (int i = 0; i < elems.length; i++) {
            addInheritedOverloaded(elems[i]);
        }
    }

    private void addInheritedOverloaded(Symbol sym) {
	if (sym.isMethod() && !sym.isConstructor()) {
            sym.addInheritedOverloaded(sym.type());
        }
    }

    /** convert Java modifiers into Scala flags
     */
    public int transFlags(int flags) {
        int res = 0;
        if ((flags & JAVA_ACC_PRIVATE) != 0)
            res |= Modifiers.PRIVATE;
        else if ((flags & JAVA_ACC_PROTECTED) != 0)
            res |= Modifiers.PROTECTED;
        else if ((flags & JAVA_ACC_PUBLIC) == 0)
            res |= Modifiers.PRIVATE;
        if ((flags & JAVA_ACC_ABSTRACT) != 0)
            res |= Modifiers.DEFERRED;
        if ((flags & JAVA_ACC_FINAL) != 0)
            res |= Modifiers.FINAL;
        if ((flags & JAVA_ACC_INTERFACE) != 0)
            res |= Modifiers.INTERFACE | Modifiers.TRAIT | Modifiers.ABSTRACT;
        if ((flags & JAVA_ACC_SYNTHETIC) != 0)
            res |= Modifiers.SYNTHETIC;
        return res | Modifiers.JAVA;
    }

    /** read a class name and return the corresponding class type
     */
    protected Type readClassType(int i) {
        return i == 0 ? make.anyType() : make.classType(pool.getClass(i));
    }

    /** read a field
     */
    protected void parseField() {
        int jflags = in.nextChar();
        int sflags = transFlags(jflags);
        if ((jflags & JAVA_ACC_FINAL) == 0) sflags |= Modifiers.MUTABLE;
        if ((sflags & Modifiers.PRIVATE) != 0) {
            in.skip(4);
            attrib.skipAttributes();
        } else {
            Name name = pool.getName(in.nextChar());
            Symbol owner = getOwner(jflags);
            if ((jflags & JAVA_ACC_STATIC) == 0 && shouldTreatAsJavaCaseClass(c))
                sflags |= Modifiers.SYNTHETIC;
            Symbol symbol = owner.newTerm(Position.NOPOS, sflags, name);
            Type type = pool.getFieldType(in.nextChar());
            symbol.setInfo(type);
            attrib.readAttributes(symbol, type, FIELD_ATTR);
            if (!isFoundationNullaryBridge(name))
                getScope(jflags).enterOrOverload(symbol);
        }
    }

    private boolean shouldTreatAsJavaCaseClass(Symbol clazz) {
        String fullname = Debug.show(clazz);
        if (fullname.startsWith("scalac.ast.Tree$") ||
            fullname.startsWith("scalac.ast.Tree.")) {
            return !(fullname.startsWith("scalac.ast.Tree$Ext") ||
                     fullname.startsWith("scalac.ast.Tree.Ext"));
        }
        if (fullname.startsWith("scalac.atree.AConstant$") ||
            fullname.startsWith("scalac.atree.AConstant.") ||
            fullname.startsWith("scalac.atree.APrimitive$") ||
            fullname.startsWith("scalac.atree.APrimitive.") ||
            fullname.startsWith("scalac.atree.ACode$") ||
            fullname.startsWith("scalac.atree.ACode.") ||
            fullname.startsWith("scalac.atree.ALocation$") ||
            fullname.startsWith("scalac.atree.ALocation.") ||
            fullname.startsWith("scalac.atree.AFunction$") ||
            fullname.startsWith("scalac.atree.AFunction.")) {
            return true;
        }
        if (!(fullname.startsWith("scalac.symtab.Type$") ||
              fullname.startsWith("scalac.symtab.Type."))) {
            return false;
        }
        return fullname.endsWith("ErrorType") ||
               fullname.endsWith("AnyType") ||
               fullname.endsWith("NoType") ||
               fullname.endsWith("ThisType") ||
               fullname.endsWith("SingleType") ||
               fullname.endsWith("ConstantType") ||
               fullname.endsWith("TypeRef") ||
               fullname.endsWith("CompoundType") ||
               fullname.endsWith("MethodType") ||
               fullname.endsWith("PolyType") ||
               fullname.endsWith("OverloadedType") ||
               fullname.endsWith("TypeVar") ||
               fullname.endsWith("UnboxedType") ||
               fullname.endsWith("UnboxedArrayType");
    }

    /** read a method
     */
    protected void parseMethod() {
        int jflags = in.nextChar();
        int sflags = transFlags(jflags);
        if ((jflags & JAVA_ACC_BRIDGE) != 0) {
            in.skip(4);
            attrib.skipAttributes();
            return;
        }
        if ((sflags & Modifiers.PRIVATE) != 0) {
            in.skip(4);
            attrib.skipAttributes();
        } else {
            Name rawName = pool.getName(in.nextChar());
            Name name = foundationMethodName(rawName);
            Type type = pool.getMethodType(in.nextChar());
            Symbol owner = getOwner(jflags);
            Symbol symbol;
            boolean newConstructor = false;
            if (name == CONSTR_N) {
                if (type instanceof Type.MethodType) {
                    Symbol[] vparams = ((Type.MethodType)type).vparams;
                    type = Type.MethodType(vparams, ctype);
                } else {
                    throw Debug.abort("illegal case", type);
                }
                symbol = owner.primaryConstructor();
                if (symbol.isInitialized()) {
                    symbol = owner.newConstructor(Position.NOPOS, sflags);
                    newConstructor = true;
                } else {
                    symbol.flags = sflags;
                }
            } else {
                symbol = owner.newTerm(Position.NOPOS, sflags, name);
            }
            setParamOwners(type, symbol);
            symbol.setInfo(type);
            attrib.readAttributes(symbol, type, METH_ATTR);
            Type parameterlessType = foundationParameterlessType(rawName, symbol.type());
            if (parameterlessType != null)
                symbol.setInfo(parameterlessType);
            if (name != CONSTR_N) getScope(jflags).enterOrOverload(symbol);
            else if (newConstructor) owner.addConstructor(symbol);
        }
    }

    /** return the owner of a member with given java flags
     */
    private Symbol getOwner(int jflags) {
        return (jflags & JAVA_ACC_STATIC) != 0 ? m.moduleClass() : c;
    }

    /** return the scope of a member with given java flags
     */
    private Scope getScope(int jflags) {
        return (jflags & JAVA_ACC_STATIC) != 0 ? statics : locals;
    }

    private boolean isNullaryMethodType(Type type) {
        if (type instanceof Type.MethodType)
            return ((Type.MethodType)type).vparams.length == 0;
        if (type instanceof Type.PolyType)
            return isNullaryMethodType(((Type.PolyType)type).result);
        return false;
    }

    private Name foundationMethodName(Name name) {
        String fullname = Debug.show(c);
        if (!fullname.equals("scala.Float") &&
            !fullname.equals("scala.Long") &&
            !fullname.equals("scala.Int") &&
            !fullname.equals("scala.Byte") &&
            !fullname.equals("scala.Short") &&
            !fullname.equals("scala.Char")) {
            return name;
        }
        if (name == Names.coerceToDouble ||
            name == Names.coerceToFloat ||
            name == Names.coerceToLong ||
            name == Names.coerceToInt ||
            name == Names.coerceToShort) {
            return Names.coerce;
        }
        return name;
    }

    private boolean isFoundationNullaryBridge(Name name) {
        return foundationNullaryResult(name) != null;
    }

    private Type foundationParameterlessType(Name name, Type type) {
        if (!isNullaryMethodType(type))
            return null;
        Type result = foundationNullaryResult(name);
        return result == null ? null : Type.PolyType(Symbol.EMPTY_ARRAY, result);
    }

    private Type foundationNullaryResult(Name name) {
        String fullname = Debug.show(c);
        Definitions definitions = global.definitions;
        if (fullname.equals("scala.Boolean"))
            return name == Names.BANG ? definitions.BOOLEAN_CLASS.typeConstructor() : null;

        if (fullname.equals("scala.Double"))
            return (name == Names.PLUS || name == Names.MINUS) ? definitions.DOUBLE_CLASS.typeConstructor() : null;

        if (fullname.equals("scala.Float")) {
            if (name == Names.PLUS || name == Names.MINUS)
                return definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return definitions.DOUBLE_CLASS.typeConstructor();
            return null;
        }

        if (fullname.equals("scala.Long")) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE)
                return definitions.LONG_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return definitions.FLOAT_CLASS.typeConstructor();
            return null;
        }

        if (fullname.equals("scala.Int")) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE)
                return definitions.INT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToLong)
                return definitions.LONG_CLASS.typeConstructor();
            return null;
        }

        if (fullname.equals("scala.Byte") || fullname.equals("scala.Short") || fullname.equals("scala.Char")) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE || name == Names.coerceToInt)
                return definitions.INT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToLong)
                return definitions.LONG_CLASS.typeConstructor();
            if (fullname.equals("scala.Byte") && name == Names.coerceToShort)
                return definitions.SHORT_CLASS.typeConstructor();
            if (fullname.equals("scala.Char") &&
                (name == Name.fromString("isDigit") ||
                 name == Name.fromString("isLetter") ||
                 name == Name.fromString("isLetterOrDigit") ||
                 name == Name.fromString("isWhitespace")))
                return definitions.BOOLEAN_CLASS.typeConstructor();
            return null;
        }

        return null;
    }

    private void setParamOwners(Type type, Symbol owner) {
        if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)type;
            Symbol[] params = polyType.tparams;
            for (int i = 0; i < params.length; i++)
                params[i].setOwner(owner);
            setParamOwners(polyType.result, owner);
        } else if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            Symbol[] params = methodType.vparams;
            for (int i = 0; i < params.length; i++) params[i].setOwner(owner);
            setParamOwners(methodType.result, owner);
        }
    }

    private void patchFoundationClassInfo() {
        String fullname = Debug.show(c);
        Definitions definitions = global.definitions;
        if (fullname.equals("scala.AnyVal")) {
            patchClassParents(new Type[] { definitions.ANY_TYPE() });
            return;
        }
        if (fullname.equals("scala.ScalaObject")) {
            patchClassParents(new Type[] { definitions.OBJECT_TYPE() });
            return;
        }
        for (int arity = 0; arity < definitions.FUNCTION_COUNT; arity++) {
            if (fullname.equals("scala.Function" + arity)) {
                patchFunctionClassInfo(arity);
                return;
            }
        }
        if (fullname.equals("scala.Array")) {
            patchArrayClassInfo();
            return;
        }
        if (fullname.equals("scala.Ref")) {
            patchRefClassInfo();
            return;
        }
        if (fullname.equals("scala.runtime.ResultOrException")) {
            patchResultOrExceptionClassInfo();
            return;
        }
        if (fullname.equals("scala.runtime.NativeLoop")) {
            patchNativeLoopClassInfo();
            return;
        }
        if (fullname.equals("scala.MatchError")) {
            patchMatchErrorClassInfo();
        }
    }

    private void patchClassParents(Type[] parents) {
        Type info = c.info();
        if (info instanceof Type.CompoundType) {
            Type.CompoundType compound = (Type.CompoundType)info;
            c.setInfo(Type.compoundType(parents, compound.members, c));
        }
    }

    private Symbol newTParam(Symbol owner, int index, int variance, Type bound) {
        Name name = Name.fromString("T" + index).toTypeName();
        return owner.newTParam(Position.NOPOS, variance, name, bound);
    }

    private Symbol newVParam(Symbol owner, int index, Type type) {
        Name name = Name.fromString("v" + index);
        return owner.newVParam(Position.NOPOS, 0, name, type);
    }

    private Symbol newDefParam(Symbol owner, int index, Type type) {
        Name name = Name.fromString("v" + index);
        return owner.newVParam(Position.NOPOS, Modifiers.DEF, name, type);
    }

    private void patchFunctionClassInfo(int arity) {
        Definitions definitions = global.definitions;
        Symbol constr = c.primaryConstructor();
        Symbol[] tparams = new Symbol[arity + 1];
        for (int i = 0; i < arity; i++)
            tparams[i] = newTParam(constr, i, Modifiers.CONTRAVARIANT, definitions.ANY_TYPE());
        tparams[arity] = newTParam(constr, arity, Modifiers.COVARIANT, definitions.ANY_TYPE());
        Type restype = Type.appliedType(c.typeConstructor(), Symbol.type(tparams));
        constr.setInfo(Type.PolyType(tparams, Type.MethodType(Symbol.EMPTY_ARRAY, restype)));

        Symbol apply = c.lookup(Names.apply);
        if (apply != Symbol.NONE) {
            Symbol method = apply.firstAlternative();
            Symbol[] vparams = new Symbol[arity];
            for (int i = 0; i < arity; i++)
                vparams[i] = newVParam(method, i, tparams[i].type());
            method.setInfo(Type.MethodType(vparams, tparams[arity].type()));
        }
    }

    private void patchArrayClassInfo() {
        Definitions definitions = global.definitions;
        Symbol constr = c.primaryConstructor();
        Symbol elem = newTParam(constr, 0, 0, definitions.ANY_TYPE());
        Type arrayType = Type.appliedType(c.typeConstructor(), new Type[] { elem.type() });
        Symbol length = newVParam(constr, 0, definitions.INT_TYPE());
        constr.setInfo(Type.PolyType(new Symbol[] { elem }, Type.MethodType(new Symbol[] { length }, arrayType)));

        Type classInfo = c.info();
        if (classInfo instanceof Type.CompoundType) {
            Type.CompoundType compound = (Type.CompoundType)classInfo;
            Type[] parts = Type.cloneArray(compound.parts);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].symbol() == definitions.FUNCTION_CLASS[1]) {
                    parts[i] = Type.appliedType(
                        definitions.FUNCTION_CLASS[1].typeConstructor(),
                        new Type[] { definitions.INT_TYPE(), elem.type() });
                }
            }
            c.setInfo(Type.compoundType(parts, compound.members, c));
        }

        patchArrayMethod(Names.apply, new Type[] { definitions.INT_TYPE() }, elem.type());
        patchArrayMethod(Names.update, new Type[] { definitions.INT_TYPE(), elem.type() }, definitions.UNIT_TYPE());
        patchArrayMethod(Names.length, Type.EMPTY_ARRAY, definitions.INT_TYPE());
        patchArrayMethod(Names.foreach,
            new Type[] { definitions.FUNCTION_TYPE(new Type[] { elem.type() }, definitions.UNIT_TYPE()) },
            definitions.UNIT_TYPE());
    }

    private void patchRefClassInfo() {
        Definitions definitions = global.definitions;
        Symbol constr = c.primaryConstructor();
        Symbol elem = newTParam(constr, 0, 0, definitions.ANY_TYPE());
        Type refType = Type.appliedType(c.typeConstructor(), new Type[] { elem.type() });
        Symbol value = newVParam(constr, 0, elem.type());
        constr.setInfo(Type.PolyType(new Symbol[] { elem }, Type.MethodType(new Symbol[] { value }, refType)));

        Symbol field = c.lookup(Names.elem);
        if (field != Symbol.NONE)
            field.setInfo(elem.type());
    }

    private void patchResultOrExceptionClassInfo() {
        Definitions definitions = global.definitions;
        Symbol constr = c.primaryConstructor();
        Symbol res = newTParam(constr, 0, 0, definitions.ANY_TYPE());
        Type roeType = Type.appliedType(c.typeConstructor(), new Type[] { res.type() });
        Symbol result = newVParam(constr, 0, res.type());
        Symbol ex = newVParam(constr, 1, definitions.THROWABLE_TYPE());
        constr.setInfo(Type.PolyType(new Symbol[] { res }, Type.MethodType(new Symbol[] { result, ex }, roeType)));

        Symbol resultField = c.lookup(Name.fromString("result"));
        if (resultField != Symbol.NONE)
            resultField.setInfo(res.type());

        Symbol tryBlock = c.linkedModule().moduleClass().lookup(Name.fromString("tryBlock"));
        if (tryBlock != Symbol.NONE) {
            Symbol method = tryBlock.firstAlternative();
            Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, definitions.ANY_TYPE()) };
            Type resultType = Type.appliedType(c.typeConstructor(), new Type[] { tparams[0].type() });
            Symbol block = newDefParam(method, 0, tparams[0].type());
            method.setInfo(Type.PolyType(tparams, Type.MethodType(new Symbol[] { block }, resultType)));
        }
    }

    private void patchNativeLoopClassInfo() {
        Definitions definitions = global.definitions;
        Symbol loopWhile = c.linkedModule().moduleClass().lookup(Name.fromString("loopWhile"));
        if (loopWhile == Symbol.NONE) return;
        Symbol method = loopWhile.firstAlternative();
        Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, definitions.ANY_TYPE()) };
        Symbol cond = newDefParam(method, 0, definitions.BOOLEAN_TYPE());
        Symbol body = newDefParam(method, 1, tparams[0].type());
        method.setInfo(
            Type.PolyType(
                tparams,
                Type.MethodType(
                    new Symbol[] { cond, body },
                    definitions.UNIT_TYPE())));
    }

    private void patchMatchErrorClassInfo() {
        Definitions definitions = global.definitions;
        Symbol fail = c.linkedModule().moduleClass().lookup(Names.fail);
        if (fail != Symbol.NONE) {
            Symbol method = fail.firstAlternative();
            Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, definitions.ANY_TYPE()) };
            Symbol source = newVParam(method, 0, definitions.STRING_TYPE());
            Symbol line = newVParam(method, 1, definitions.INT_TYPE());
            method.setInfo(
                Type.PolyType(
                    tparams,
                    Type.MethodType(
                        new Symbol[] { source, line },
                        tparams[0].type())));
        }
        Symbol report = c.linkedModule().moduleClass().lookup(Names.report);
        if (report == Symbol.NONE) return;
        Symbol method = report.firstAlternative();
        Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, definitions.ANY_TYPE()) };
        Symbol source = newVParam(method, 0, definitions.STRING_TYPE());
        Symbol line = newVParam(method, 1, definitions.INT_TYPE());
        Symbol obj = newVParam(method, 2, definitions.ANY_TYPE());
        method.setInfo(
            Type.PolyType(
                tparams,
                Type.MethodType(
                    new Symbol[] { source, line, obj },
                    tparams[0].type())));
    }

    private void patchArrayMethod(Name name, Type[] argTypes, Type resultType) {
        Symbol symbol = c.lookup(name);
        if (symbol == Symbol.NONE) return;
        Symbol[] alts = symbol.alternativeSymbols();
        if (patchArrayMethod(alts, argTypes, resultType, true) ||
            patchArrayMethod(alts, argTypes, resultType, false)) {
            if (symbol.type() instanceof Type.OverloadedType)
                symbol.setInfo(Type.OverloadedType(alts, Symbol.type(alts)));
        }
    }

    private boolean patchArrayMethod(Symbol[] alts, Type[] argTypes, Type resultType, boolean requireArrayOwner) {
        for (int i = 0; i < alts.length; i++) {
            if (requireArrayOwner && alts[i].owner() != c) continue;
            Type altType = alts[i].type();
            if (argTypes.length == 0 && !(altType instanceof Type.MethodType)) {
                alts[i].setInfo(Type.PolyType(Symbol.EMPTY_ARRAY, resultType));
                return true;
            }
            if (altType instanceof Type.MethodType) {
                Symbol[] vparams = ((Type.MethodType)altType).vparams;
                if (matchesArrayPatchParameters(vparams, argTypes)) {
                    Symbol[] params = new Symbol[argTypes.length];
                    for (int j = 0; j < argTypes.length; j++)
                        params[j] = newVParam(alts[i], j, argTypes[j]);
                    alts[i].setInfo(
                        params.length == 0 ? Type.PolyType(Symbol.EMPTY_ARRAY, resultType) : Type.MethodType(params, resultType));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesArrayPatchParameters(Symbol[] params, Type[] argTypes) {
        if (params.length != argTypes.length) return false;
        for (int i = 0; i < params.length; i++) {
            Type paramType = params[i].type();
            if (!paramType.isSameAs(argTypes[i]) &&
                paramType.symbol() != argTypes[i].symbol())
                return false;
        }
        return true;
    }
}
