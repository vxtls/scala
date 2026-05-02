/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scala.tools.util.Position;

import scalac.Global;
import scalac.symtab.Definitions;
import scalac.symtab.Modifiers;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;
import scalac.util.Name;

/** This class implements an attributed tree typer. */
public class ATreeTyper {

    //########################################################################
    // Private Fields

    /** The global environment */
    public final Global global;

    /** The global definitions */
    private final Definitions definitions;

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public ATreeTyper(Global global, Definitions definitions) {
        this.global = global;
        this.definitions = definitions;
    }

    public ATreeTyper(Global global) {
        this(global, global.definitions);
    }

    //########################################################################
    // Public Methods - Typing code

    /** Returns the types of the given codes. */
    public Type[] type(ACode[] codes) {
        Type[] types = new Type[codes.length];
        for (int i = 0; i < types.length; i++) types[i] = type(codes[i]);
        return types;
    }

    /** Returns the type of the given code. */
    public Type type(ACode code) {
        if (code == ACode.Void) {
            return definitions.UNIT_TYPE();
        } else if (code instanceof ACode.This) {
            return ((ACode.This) code).clasz.thisType();
        } else if (code instanceof ACode.Constant) {
            return type(((ACode.Constant) code).constant);
        } else if (code instanceof ACode.Load) {
            return type(((ACode.Load) code).location);
        } else if (code instanceof ACode.Store) {
            return Type.NoType;
        } else if (code instanceof ACode.Apply) {
            ACode.Apply applyCode = (ACode.Apply) code;
            return apply(type(applyCode.function), applyCode.targs).resultType();
        } else if (code instanceof ACode.IsAs) {
            ACode.IsAs isAs = (ACode.IsAs) code;
            return isAs.cast ? isAs.type : definitions.BOOLEAN_TYPE();
        } else if (code instanceof ACode.If) {
            ACode.If ifCode = (ACode.If) code;
            return Type.lub(new Type[]{type(ifCode.success), type(ifCode.failure)});
        } else if (code instanceof ACode.Switch) {
            return Type.lub(type(((ACode.Switch) code).bodies));
        } else if (code instanceof ACode.Synchronized) {
            return type(((ACode.Synchronized) code).value);
        } else if (code instanceof ACode.Block) {
            return type(((ACode.Block) code).value);
        } else if (code instanceof ACode.Label) {
            return ((ACode.Label) code).label.type().resultType();
        } else if (code instanceof ACode.Goto ||
                   code instanceof ACode.Return ||
                   code instanceof ACode.Throw ||
                   code instanceof ACode.Drop) {
            return Type.NoType;
        } else {
            throw Debug.abort("unknown case", code);
        }
    }

    //########################################################################
    // Public Methods - Typing value locations

    /** Returns the type of the given value location. */
    public Type type(ALocation location) {
        if (location instanceof ALocation.Module) {
            return ((ALocation.Module) location).module.thisType();
        } else if (location instanceof ALocation.Field) {
            ALocation.Field field = (ALocation.Field) location;
            if (field.object == ACode.Void) {
                return field.field.owner().thisType().memberStabilizedType(field.field);
            }
            return type(field.object).memberStabilizedType(field.field);
        } else if (location instanceof ALocation.Local) {
            return ((ALocation.Local) location).local.type();
        } else if (location instanceof ALocation.ArrayItem) {
            return getArrayElementType(type(((ALocation.ArrayItem) location).array));
        } else {
            throw Debug.abort("unknown case", location);
        }
    }

    //########################################################################
    // Public Methods - Typing function references

    /** Returns the type of the given function reference. */
    public Type type(AFunction function) {
        if (function instanceof AFunction.Method) {
            AFunction.Method method = (AFunction.Method) function;
            if (method.object == ACode.Void) {
                Type type = method.method.owner().thisType().memberStabilizedType(method.method);
                if (method.style ==  AInvokeStyle.New) {
                    assert method.method.isInitializer(): function;
                    Symbol[] tparams = method.method.owner().typeParams();
                    if (tparams.length != 0) type = Type.PolyType(tparams, type);
                }
                return type;
            }
            return type(method.object).memberStabilizedType(method.method);
        } else if (function instanceof AFunction.Primitive) {
            return type(((AFunction.Primitive) function).primitive);
        } else if (function instanceof AFunction.NewArray) {
            return definitions.ARRAY_TYPE(((AFunction.NewArray) function).element);
        } else {
            throw Debug.abort("unknown case", function);
        }
    }

    //########################################################################
    // Public Methods - Typing primitives

    /** Returns the type of the given primitive. */
    public Type type(APrimitive primitive) {
        if (primitive instanceof APrimitive.Negation) {
            Type type = type(((APrimitive.Negation) primitive).kind);
            return getMethodType(type, type);
        } else if (primitive instanceof APrimitive.Test) {
            APrimitive.Test test = (APrimitive.Test) primitive;
            Type type = type(test.kind);
            return test.zero
                ? getMethodType(type, type(ATypeKind.BOOL))
                : getMethodType(type, type, type(ATypeKind.BOOL));
        } else if (primitive instanceof APrimitive.Comparison) {
            Type type = type(((APrimitive.Comparison) primitive).kind);
            return getMethodType(type, type, type(ATypeKind.I4));
        } else if (primitive instanceof APrimitive.Arithmetic) {
            Type type = type(((APrimitive.Arithmetic) primitive).kind);
            return getMethodType(type, type, type);
        } else if (primitive instanceof APrimitive.Logical) {
            Type type = type(((APrimitive.Logical) primitive).kind);
            return getMethodType(type, type, type);
        } else if (primitive instanceof APrimitive.Shift) {
            Type type = type(((APrimitive.Shift) primitive).kind);
            return getMethodType(type, type(ATypeKind.I4), type);
        } else if (primitive instanceof APrimitive.Conversion) {
            APrimitive.Conversion conversion = (APrimitive.Conversion) primitive;
            return getMethodType(type(conversion.src), type(conversion.dst));
        } else if (primitive instanceof APrimitive.ArrayLength) {
            Type type = definitions.ARRAY_TYPE(type(((APrimitive.ArrayLength) primitive).kind));
            return getMethodType(type, type(ATypeKind.I4));
        } else if (primitive instanceof APrimitive.StringConcat) {
            APrimitive.StringConcat concat = (APrimitive.StringConcat) primitive;
            return getMethodType(type(concat.lf), type(concat.rg), type(ATypeKind.STR));
        } else {
            throw Debug.abort("unknown case", primitive);
        }
    }

    //########################################################################
    // Public Methods - Typing constants

    /** Returns the type of the given constant. */
    public Type type(AConstant constant) {
        Type base = basetype(constant);
        if (global.currentPhase.id > global.PHASE.ERASURE.id()) return base;
        return Type.ConstantType(base, constant);
    }

    /** Returns the base type of the given constant. */
    public Type basetype(AConstant constant) {
        if (constant == AConstant.UNIT) return definitions.UNIT_TYPE();
        if (constant instanceof AConstant.BooleanValue) return definitions.BOOLEAN_TYPE();
        if (constant instanceof AConstant.ByteValue) return definitions.BYTE_TYPE();
        if (constant instanceof AConstant.ShortValue) return definitions.SHORT_TYPE();
        if (constant instanceof AConstant.CharValue) return definitions.CHAR_TYPE();
        if (constant instanceof AConstant.IntValue) return definitions.INT_TYPE();
        if (constant instanceof AConstant.LongValue) return definitions.LONG_TYPE();
        if (constant instanceof AConstant.FloatValue) return definitions.FLOAT_TYPE();
        if (constant instanceof AConstant.DoubleValue) return definitions.DOUBLE_TYPE();
        if (constant instanceof AConstant.StringValue) return definitions.STRING_TYPE();
        if (constant instanceof AConstant.SymbolNameValue) return definitions.STRING_TYPE();
        if (constant == AConstant.NULL) return definitions.ALLREF_TYPE();
        if (constant == AConstant.ZERO) return definitions.ALL_TYPE();
        throw Debug.abort("unknown case", constant);
    }

    //########################################################################
    // Public Methods - Typing type kinds

    /** Returns the type of the given type kind. */
    public Type type(ATypeKind kind) {
        if (kind == ATypeKind.UNIT) return definitions.UNIT_TYPE();
        if (kind == ATypeKind.BOOL) return definitions.BOOLEAN_TYPE();
 // !!! case U1  : return ?;
        if (kind == ATypeKind.U2) return definitions.CHAR_TYPE();
 // !!! case U4  : return ?;
 // !!! case U8  : return ?;
        if (kind == ATypeKind.I1) return definitions.BYTE_TYPE();
        if (kind == ATypeKind.I2) return definitions.SHORT_TYPE();
        if (kind == ATypeKind.I4) return definitions.INT_TYPE();
        if (kind == ATypeKind.I8) return definitions.LONG_TYPE();
        if (kind == ATypeKind.R4) return definitions.FLOAT_TYPE();
        if (kind == ATypeKind.R8) return definitions.DOUBLE_TYPE();
        if (kind == ATypeKind.REF) return definitions.ANYREF_TYPE();
        if (kind == ATypeKind.STR) return definitions.STRING_TYPE();
        if (kind == ATypeKind.NULL) return definitions.ALLREF_TYPE();
        if (kind == ATypeKind.ZERO) return definitions.ALL_TYPE();
        throw Debug.abort("unknown case", kind);
    }

    //########################################################################
    // Public Methods - Aliases for scala

    public Type[] computeType(ACode[] codes) {
        return type(codes);
    }

    public Type computeType(ACode code) {
        return type(code);
    }

    public Type computeType(ALocation location) {
        return type(location);
    }

    public Type computeType(AFunction function) {
        return type(function);
    }

    public Type computeType(APrimitive primitive) {
        return type(primitive);
    }

    public Type computeType(AConstant constant) {
        return type(constant);
    }

    public Type computeType(ATypeKind kind) {
        return type(kind);
    }

    //########################################################################
    // Private Methods

    /** Returns the application of given arguments to given type. */
    private Type apply(Type type, Type[] targs) {
        if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType) type;
            return polyType.result.subst(polyType.tparams, targs);
        } else {
            assert targs.length == 0: type + " -- " + Debug.show(targs);
            return type;
        }
    }

    /** Returns the element type of the given array type. */
    public Type getArrayElementType(Type type) { // !!! public / private
        if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef) type;
            assert typeRef.sym == definitions.ARRAY_CLASS && typeRef.args.length == 1: type;
            return typeRef.args[0];
        } else if (type instanceof Type.UnboxedArrayType) {
            return ((Type.UnboxedArrayType) type).elemtp;
        } else {
            throw Debug.abort("non-array type", type);
        }
    }

    /** Returns a method type with given argument and result types. */
    private Type getMethodType(Type targ1, Type result) {
        return getMethodType(new Type[]{targ1}, result);
    }

    /** Returns a method type with given argument and result types. */
    private Type getMethodType(Type targ1, Type targ2, Type result) {
        return getMethodType(new Type[]{targ1, targ2}, result);
    }

    /** Returns a method type with given argument and result types. */
    private Type getMethodType(Type[] targs, Type result) {
        Symbol[] tparams = new Symbol[targs.length];
        for (int i = 0; i < tparams.length; i++) {
            Name name = Name.fromString("v" + i);
            tparams[i] = ((Symbol)Symbol.NONE).newTerm( // !!! should be newVParam
                Position.NOPOS, Modifiers.PARAM, name);
            tparams[i].setType(targs[i]);
        }
        return Type.MethodType(tparams, result);
    }

    //########################################################################
}
