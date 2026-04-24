/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.symtab.classfile;

import ch.epfl.lamp.util.Position;
import scalac.*;
import scalac.util.*;
import scalac.symtab.*;
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

    protected Global global;
    protected AbstractFileReader in;
    protected Symbol c;
    protected Type ctype;
    protected Scope locals;
    protected Scope statics;
    protected JavaTypeFactory make;
    protected Signatures sigs;
    protected ConstantPool pool;
    protected AttributeParser attrib;


    public ClassfileParser(Global global, AbstractFileReader in, Symbol c) {
        this.global = global;
        this.in = in;
        this.c = c;
        this.ctype = c.typeConstructor();
        this.make = new JavaTypeCreator(global.definitions);
        this.sigs = new Signatures(global, make);
        this.pool = new ConstantPool(in, sigs);
        this.attrib = new AttributeParser(in, pool, this);
    }


    /** parse the classfile and throw IO exception if there is an
     *  error in the classfile structure
     */
    public void parse() throws IOException {
        try {
            if (in.nextInt() != JAVA_MAGIC)
                throw new IOException("illegal start of class file");
            int minorVersion = in.nextChar();
            int majorVersion = in.nextChar();
            if ((majorVersion < JAVA_MAJOR_VERSION) ||
                ((majorVersion == JAVA_MAJOR_VERSION) &&
                 (minorVersion < JAVA_MINOR_VERSION)))
                throw new IOException("class file has wrong version " +
                        majorVersion + "." + minorVersion + ", should be " +
                        JAVA_MAJOR_VERSION + "." + JAVA_MINOR_VERSION);
            pool.indexPool();
            int flags = in.nextChar();
            Name name = readClassName(in.nextChar());
            if (c.fullName() != name)
                throw new IOException("class file '" + c.fullName() +
                                      "' contains wrong class " + name);
            // todo: correct flag transition
            c.flags = transFlags(flags);
            if (shouldTreatAsJavaCaseClass(c))
                c.flags |= Modifiers.CASE | Modifiers.JAVA;
            if ((c.flags & Modifiers.DEFERRED) != 0)
                c.flags = c.flags & ~Modifiers.DEFERRED | Modifiers.ABSTRACT;
            Type supertpe = readClassType(in.nextChar());
            Type[] basetpes = new Type[in.nextChar() + 1];
            this.locals = new Scope();
            this.statics = new Scope();
            // set type of class
            Type classType = Type.compoundType(basetpes, locals, c);
            c.setFirstInfo(classType);
            // set type of statics
            Symbol staticsClass = c.module().moduleClass();
            if (staticsClass.isModuleClass()) {
                Type staticsInfo = Type.compoundType(Type.EMPTY_ARRAY, statics, staticsClass);
                staticsClass.setFirstInfo(staticsInfo);
                c.module().setInfo(Type.typeRef(staticsClass.owner().thisType(),
                                            staticsClass, Type.EMPTY_ARRAY));
            }
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
                constr.setFirstInfo(
                    Type.MethodType(Symbol.EMPTY_ARRAY, ctype));
                if ((c.flags & Modifiers.INTERFACE) == 0)
                    constr.flags |= Modifiers.PRIVATE;
            }
            attrib.readAttributes(c, classType, CLASS_ATTR);
            patchFoundationClassInfo();
            //System.out.println("dynamic class: " + c);
            //System.out.println("statics class: " + staticsClass);
            //System.out.println("module: " + c.module());
            //System.out.println("modules class: " + c.module().type().symbol());
        } catch (RuntimeException e) {
            if (global.debug) e.printStackTrace();
            String s = e.getMessage() == null ? "" : " (" +e.getMessage()+ ")";
            throw new IOException("bad class file" + s);
        }
    }

    /** convert Java modifiers into Scala flags
     */
    public int transFlags(int flags) {
        int res = 0;
        if (((flags & 0x0007) == 0) ||
            ((flags & 0x0002) != 0))
            res |= Modifiers.PRIVATE;
        else if ((flags & 0x0004) != 0)
            res |= Modifiers.PROTECTED;
        if ((flags & 0x0400) != 0)
            res |= Modifiers.DEFERRED;
        if ((flags & 0x0010) != 0)
            res |= Modifiers.FINAL;
        if ((flags & 0x0200) != 0)
            res |= Modifiers.INTERFACE | Modifiers.TRAIT | Modifiers.ABSTRACT;
        return res | Modifiers.JAVA;
    }

    /** read a class name
     */
    protected Name readClassName(int i) {
        return (Name)pool.readPool(i);
    }

    /** read a class name and return the corresponding class type
     */
    protected Type readClassType(int i) {
        if (i == 0)
            return make.anyType();
        Type res = make.classType((Name)pool.readPool(i));
        if (res == Type.ErrorType)
            global.error("unknown class reference " + pool.readPool(i));
        return res;
    }

    /** read a signature and return it as a type
     */
    protected Type readType(int i) {
        Name sig = pool.readExternal(i);
        return sigs.sigToType(Name.names, sig.index, sig.length());
    }

    /** read a field
     */
    protected void parseField() {
        int flags = in.nextChar();
        Name name = (Name)pool.readPool(in.nextChar());
        Type type = readType(in.nextChar());
        int mods = transFlags(flags);
        if ((flags & 0x1000) != 0)
            mods |= Modifiers.SYNTHETIC;
        if ((flags & 0x0010) == 0)
            mods |= Modifiers.MUTABLE;
        Symbol owner = c;
        if ((flags & 0x0008) != 0)
            owner = c.module().moduleClass();
        else if (shouldTreatAsJavaCaseClass(c))
            mods |= Modifiers.SYNTHETIC;
        Symbol s = new TermSymbol(Position.NOPOS, name, owner, mods);
        s.setFirstInfo(type);
        attrib.readAttributes(s, type, FIELD_ATTR);
        if (!isFoundationNullaryBridge(name))
            ((flags & 0x0008) != 0 ? statics : locals).enterOrOverload(s);
    }

    private boolean shouldTreatAsJavaCaseClass(Symbol clazz) {
        String fullName = clazz.fullName().toString();
        if (fullName.startsWith("scalac.ast.Tree$"))
            return !fullName.startsWith("scalac.ast.Tree$Ext");
        if (fullName.startsWith("scalac.atree.AConstant$"))
            return true;
        if (fullName.startsWith("scalac.atree.APrimitive$"))
            return true;
        if (fullName.startsWith("scalac.atree.ACode$"))
            return true;
        if (fullName.startsWith("scalac.atree.ALocation$"))
            return true;
        if (fullName.startsWith("scalac.atree.AFunction$"))
            return true;
        if (!fullName.startsWith("scalac.symtab.Type$"))
            return false;
        return fullName.equals("scalac.symtab.Type$ErrorType") ||
               fullName.equals("scalac.symtab.Type$AnyType") ||
               fullName.equals("scalac.symtab.Type$NoType") ||
               fullName.equals("scalac.symtab.Type$ThisType") ||
               fullName.equals("scalac.symtab.Type$SingleType") ||
               fullName.equals("scalac.symtab.Type$ConstantType") ||
               fullName.equals("scalac.symtab.Type$TypeRef") ||
               fullName.equals("scalac.symtab.Type$CompoundType") ||
               fullName.equals("scalac.symtab.Type$MethodType") ||
               fullName.equals("scalac.symtab.Type$PolyType") ||
               fullName.equals("scalac.symtab.Type$OverloadedType") ||
               fullName.equals("scalac.symtab.Type$TypeVar") ||
               fullName.equals("scalac.symtab.Type$UnboxedType") ||
               fullName.equals("scalac.symtab.Type$UnboxedArrayType");
    }

    /** read a method
     */
    protected void parseMethod() {
        int flags = in.nextChar();
        Name name = (Name)pool.readPool(in.nextChar());
        Type type = readType(in.nextChar());
        if (CONSTR_N.equals(name)) {
            Symbol s = TermSymbol.newConstructor(c, methodMods(flags));
            // kick out package visible or private constructors
            if (((flags & 0x0002) != 0) ||
                ((flags & 0x0007) == 0)) {
                attrib.readAttributes(s, type, METH_ATTR);
                return;
            }
            if (type instanceof Type.MethodType) {
		Symbol[] vparams = ((Type.MethodType)type).vparams;
		type = Type.MethodType(vparams, ctype);
            } else {
		throw new ApplicationError();
            }
            Symbol constr = c.primaryConstructor();
            if (constr.isInitialized())
                constr = c.addConstructor();
            s.copyTo(constr);
            setParamOwners(type, constr);
            constr.setFirstInfo(type);
            attrib.readAttributes(constr, type, METH_ATTR);
            //System.out.println(c + " " + c.allConstructors() + ":" + c.allConstructors().info());//debug
            //System.out.println("-- enter " + s);
        } else {
            int mods = methodMods(flags);
            boolean useParameterlessType = shouldUseParameterlessType(name, type);
            if (useParameterlessType)
                type = asParameterlessType(type);
            Symbol s = new TermSymbol(
                Position.NOPOS, name,
                ((flags & 0x0008) != 0) ? c.module().moduleClass() : c,
                mods);
            setParamOwners(type, s);
            s.setFirstInfo(type);
            attrib.readAttributes(s, type, METH_ATTR);
            Type parameterlessType = foundationParameterlessType(name, s.type());
            if (parameterlessType != null)
                s.setFirstInfo(parameterlessType);
            if (shouldHideMethod(s, flags, useParameterlessType))
                s.flags |= Modifiers.BRIDGE;
            if ((s.flags & Modifiers.BRIDGE) == 0)
                ((flags & 0x0008) != 0 ? statics : locals).enterOrOverload(s);
        }
    }

    private int methodMods(int flags) {
        int mods = transFlags(flags);
        if ((flags & 0x0040) != 0)
            mods |= Modifiers.BRIDGE;
        if ((flags & 0x1000) != 0)
            mods |= Modifiers.SYNTHETIC;
        return mods;
    }

    private boolean shouldHideMethod(Symbol symbol, int flags, boolean useParameterlessType) {
        if ((symbol.flags & Modifiers.BRIDGE) != 0)
            return true;
        if (useParameterlessType) {
            Scope scope = ((flags & 0x0008) != 0) ? statics : locals;
            if (scope.lookup(symbol.name) != Symbol.NONE)
                return true;
        }
        if (c.fullName() == Names.scala_Array &&
            symbol.name == Names.apply &&
            (flags & 0x0400) == 0 &&
            symbol.type() instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)symbol.type();
            return methodType.vparams.length == 1 &&
                   methodType.vparams[0].type().symbol() == global.definitions.INT_CLASS;
        }
        return false;
    }

    private boolean shouldUseParameterlessType(Name name, Type type) {
        if (!isNullaryMethodType(type))
            return false;
        return c.fullName() == Names.scala_Array && name == Names.length;
    }

    private boolean isNullaryMethodType(Type type) {
        if (type instanceof Type.MethodType)
            return ((Type.MethodType)type).vparams.length == 0;
        if (type instanceof Type.PolyType)
            return isNullaryMethodType(((Type.PolyType)type).result);
        return false;
    }

    private Type asParameterlessType(Type type) {
        if (type instanceof Type.MethodType)
            return ((Type.MethodType)type).result;
        if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)type;
            return Type.PolyType(polyType.tparams, asParameterlessType(polyType.result));
        }
        return type;
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
        Name fullname = c.fullName();
        if (fullname == Names.scala_Boolean)
            return name == Names.BANG ? global.definitions.BOOLEAN_CLASS.typeConstructor() : null;

        if (fullname == Names.scala_Double)
            return (name == Names.PLUS || name == Names.MINUS) ? global.definitions.DOUBLE_CLASS.typeConstructor() : null;

        if (fullname == Names.scala_Float) {
            if (name == Names.PLUS || name == Names.MINUS)
                return global.definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return global.definitions.DOUBLE_CLASS.typeConstructor();
            return null;
        }

        if (fullname == Names.scala_Long) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE)
                return global.definitions.LONG_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return global.definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return global.definitions.FLOAT_CLASS.typeConstructor();
            return null;
        }

        if (fullname == Names.scala_Int) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE)
                return global.definitions.INT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return global.definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return global.definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToLong)
                return global.definitions.LONG_CLASS.typeConstructor();
            return null;
        }

        if (fullname == Names.scala_Byte || fullname == Names.scala_Short || fullname == Names.scala_Char) {
            if (name == Names.PLUS || name == Names.MINUS || name == Names.TILDE || name == Names.coerceToInt)
                return global.definitions.INT_CLASS.typeConstructor();
            if (name == Names.coerceToDouble)
                return global.definitions.DOUBLE_CLASS.typeConstructor();
            if (name == Names.coerceToFloat)
                return global.definitions.FLOAT_CLASS.typeConstructor();
            if (name == Names.coerceToLong)
                return global.definitions.LONG_CLASS.typeConstructor();
            if (fullname == Names.scala_Byte && name == Names.coerceToShort)
                return global.definitions.SHORT_CLASS.typeConstructor();
            if (fullname == Names.scala_Char &&
                (name == Name.fromString("isDigit") ||
                 name == Name.fromString("isLetter") ||
                 name == Name.fromString("isLetterOrDigit") ||
                 name == Name.fromString("isWhitespace")))
                return global.definitions.BOOLEAN_CLASS.typeConstructor();
            return null;
        }

        return null;
    }

    private void setParamOwners(Type type, Symbol owner) {
        if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)type;
			for (int i = 0; i < polyType.tparams.length; i++)
				polyType.tparams[i].setOwner(owner);
			setParamOwners(polyType.result, owner);
		} else if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            for (int i = 0; i < methodType.vparams.length; i++) methodType.vparams[i].setOwner(owner);
			setParamOwners(methodType.result, owner);
		}
    }

    private void patchFoundationClassInfo() {
        Name fullname = c.fullName();
        if (fullname == Names.scala_AnyVal) {
            patchClassParents(new Type[] { global.definitions.ANY_TYPE() });
            return;
        }
        if (fullname == Names.scala_ScalaObject) {
            patchClassParents(new Type[] { global.definitions.JAVA_OBJECT_TYPE() });
            return;
        }
        for (int arity = 0; arity < global.definitions.FUNCTION_COUNT; arity++) {
            if (fullname == Names.scala_Function(arity)) {
                patchFunctionClassInfo(arity);
                return;
            }
        }
        if (fullname == Names.scala_Array) {
            patchArrayClassInfo();
            return;
        }
        if (fullname == Names.scala_Ref) {
            patchRefClassInfo();
            return;
        }
        if (fullname == Name.fromString("scala.runtime.ResultOrException")) {
            patchResultOrExceptionClassInfo();
            return;
        }
        if (fullname == Name.fromString("scala.runtime.NativeLoop")) {
            patchNativeLoopClassInfo();
            return;
        }
        if (fullname == Name.fromString("scala.MatchError")) {
            patchMatchErrorClassInfo();
        }
    }

    private void patchClassParents(Type[] parents) {
        Type info = c.info();
        if (info instanceof Type.CompoundType) {
            Type.CompoundType compound = (Type.CompoundType)info;
            c.setFirstInfo(Type.compoundType(parents, compound.members, c));
        }
    }

    private Symbol newTParam(Symbol owner, int index, int variance, Type bound) {
        Name name = Name.fromString("T" + index).toTypeName();
        Symbol tparam = new AbsTypeSymbol(Position.NOPOS, name, owner, Modifiers.PARAM | variance);
        tparam.setFirstInfo(bound);
        return tparam;
    }

    private Symbol newVParam(Symbol owner, int index, Type type) {
        Name name = Name.fromString("v" + index);
        Symbol vparam = new TermSymbol(Position.NOPOS, name, owner, Modifiers.PARAM);
        vparam.setFirstInfo(type);
        return vparam;
    }

    private Symbol newDefParam(Symbol owner, int index, Type type) {
        Name name = Name.fromString("v" + index);
        Symbol vparam = new TermSymbol(Position.NOPOS, name, owner, Modifiers.PARAM | Modifiers.DEF);
        vparam.setFirstInfo(type);
        return vparam;
    }

    private void patchFunctionClassInfo(int arity) {
        Symbol constr = c.primaryConstructor();
        Symbol[] tparams = new Symbol[arity + 1];
        for (int i = 0; i < arity; i++) {
            tparams[i] = newTParam(constr, i, Modifiers.CONTRAVARIANT, global.definitions.ANY_TYPE());
        }
        tparams[arity] = newTParam(constr, arity, Modifiers.COVARIANT, global.definitions.ANY_TYPE());
        Type restype = Type.appliedType(c.typeConstructor(), Symbol.type(tparams));
        constr.setFirstInfo(Type.PolyType(tparams, Type.MethodType(Symbol.EMPTY_ARRAY, restype)));

        Symbol apply = c.lookup(Names.apply);
        if (apply != Symbol.NONE) {
            Symbol method = apply.firstAlternative();
            Symbol[] vparams = new Symbol[arity];
            for (int i = 0; i < arity; i++) {
                vparams[i] = newVParam(method, i, tparams[i].type());
            }
            method.setFirstInfo(Type.MethodType(vparams, tparams[arity].type()));
        }
    }

    private void patchArrayClassInfo() {
        Symbol constr = c.primaryConstructor();
        Symbol elem = newTParam(constr, 0, 0, global.definitions.ANY_TYPE());
        Type arrayType = Type.appliedType(c.typeConstructor(), new Type[] { elem.type() });
        Symbol length = newVParam(constr, 0, global.definitions.INT_CLASS.type());
        constr.setFirstInfo(Type.PolyType(new Symbol[] { elem }, Type.MethodType(new Symbol[] { length }, arrayType)));

        Type classInfo = c.info();
        if (classInfo instanceof Type.CompoundType) {
            Type.CompoundType compound = (Type.CompoundType)classInfo;
            Type[] parts = Type.cloneArray(compound.parts);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].symbol() == global.definitions.FUNCTION_CLASS[1]) {
                    parts[i] = Type.appliedType(
                        global.definitions.FUNCTION_CLASS[1].typeConstructor(),
                        new Type[] { global.definitions.INT_CLASS.type(), elem.type() });
                }
            }
            c.setFirstInfo(Type.compoundType(parts, compound.members, c));
        }

        patchArrayMethod(Names.apply, new Type[] { global.definitions.INT_CLASS.type() }, elem.type());
        patchArrayMethod(Names.update, new Type[] { global.definitions.INT_CLASS.type(), elem.type() }, global.definitions.UNIT_CLASS.type());
        patchArrayMethod(Names.length, Type.EMPTY_ARRAY, global.definitions.INT_CLASS.type());
    }

    private void patchRefClassInfo() {
        Symbol constr = c.primaryConstructor();
        Symbol elem = newTParam(constr, 0, 0, global.definitions.ANY_TYPE());
        Type refType = Type.appliedType(c.typeConstructor(), new Type[] { elem.type() });
        Symbol value = newVParam(constr, 0, elem.type());
        constr.setFirstInfo(Type.PolyType(new Symbol[] { elem }, Type.MethodType(new Symbol[] { value }, refType)));

        Symbol field = c.lookup(Names.elem);
        if (field != Symbol.NONE) {
            field.setFirstInfo(elem.type());
        }
    }

    private void patchResultOrExceptionClassInfo() {
        Symbol constr = c.primaryConstructor();
        Symbol res = newTParam(constr, 0, 0, global.definitions.ANY_TYPE());
        Type roeType = Type.appliedType(c.typeConstructor(), new Type[] { res.type() });
        Symbol result = newVParam(constr, 0, res.type());
        Symbol ex = newVParam(constr, 1, global.definitions.JAVA_THROWABLE_CLASS.type());
        constr.setFirstInfo(Type.PolyType(new Symbol[] { res }, Type.MethodType(new Symbol[] { result, ex }, roeType)));

        Symbol resultField = c.lookup(Name.fromString("result"));
        if (resultField != Symbol.NONE) {
            resultField.setFirstInfo(res.type());
        }

        Symbol tryBlock = c.module().moduleClass().lookup(Name.fromString("tryBlock"));
        if (tryBlock != Symbol.NONE) {
            Symbol method = tryBlock.firstAlternative();
            Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, global.definitions.ANY_TYPE()) };
            Type resultType = Type.appliedType(c.typeConstructor(), new Type[] { tparams[0].type() });
            Symbol block = newDefParam(method, 0, tparams[0].type());
            method.setFirstInfo(Type.PolyType(tparams, Type.MethodType(new Symbol[] { block }, resultType)));
        }
    }

    private void patchNativeLoopClassInfo() {
        Symbol loopWhile = c.module().moduleClass().lookup(Name.fromString("loopWhile"));
        if (loopWhile == Symbol.NONE) {
            return;
        }
        Symbol method = loopWhile.firstAlternative();
        Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, global.definitions.ANY_TYPE()) };
        Symbol cond = newDefParam(method, 0, global.definitions.BOOLEAN_CLASS.type());
        Symbol body = newDefParam(method, 1, tparams[0].type());
        method.setFirstInfo(
            Type.PolyType(
                tparams,
                Type.MethodType(
                    new Symbol[] { cond, body },
                    global.definitions.UNIT_CLASS.type())));
    }

    private void patchMatchErrorClassInfo() {
        Symbol fail = c.module().moduleClass().lookup(Names.fail);
        if (fail == Symbol.NONE) {
            return;
        }
        Symbol method = fail.firstAlternative();
        Symbol[] tparams = new Symbol[] { newTParam(method, 0, 0, global.definitions.ANY_TYPE()) };
        Symbol source = newVParam(method, 0, global.definitions.JAVA_STRING_CLASS.type());
        Symbol line = newVParam(method, 1, global.definitions.INT_CLASS.type());
        method.setFirstInfo(
            Type.PolyType(
                tparams,
                Type.MethodType(
                    new Symbol[] { source, line },
                    tparams[0].type())));
    }

    private void patchArrayMethod(Name name, Type[] argTypes, Type resultType) {
        Symbol symbol = c.lookup(name);
        if (symbol == Symbol.NONE) {
            return;
        }
        Symbol[] alts = symbol.alternativeSymbols();
        for (int i = 0; i < alts.length; i++) {
            Type altType = alts[i].type();
            if (argTypes.length == 0 && !(altType instanceof Type.MethodType)) {
                alts[i].setFirstInfo(resultType);
                return;
            }
            if (altType instanceof Type.MethodType) {
                Symbol[] vparams = ((Type.MethodType)altType).vparams;
                if (Type.isSameAs(Symbol.type(vparams), argTypes)) {
                    Symbol[] params = new Symbol[argTypes.length];
                    for (int j = 0; j < argTypes.length; j++) {
                        params[j] = newVParam(alts[i], j, argTypes[j]);
                    }
                    alts[i].setFirstInfo(
                        params.length == 0 ? resultType : Type.MethodType(params, resultType));
                    return;
                }
            }
        }
    }
}
