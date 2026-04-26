/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: JavaMirror.java,v 1.8 2002/10/01 16:14:07 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.HashMap;

import scalac.symtab.Kinds;
import scalac.symtab.TypeTags;
import scalac.symtab.Type;
import scalac.symtab.Symbol;
import scalac.symtab.Definitions;
import scalac.util.Debug;

public class JavaMirror {

    //########################################################################
    // Private Constants

    private static final Class void_class    = Void.TYPE;
    private static final Class boolean_class = Boolean.TYPE;
    private static final Class byte_class    = Byte.TYPE;
    private static final Class short_class   = Short.TYPE;
    private static final Class char_class    = Character.TYPE;
    private static final Class int_class     = Integer.TYPE;
    private static final Class long_class    = Long.TYPE;
    private static final Class float_class   = Float.TYPE;
    private static final Class double_class  = Double.TYPE;
    private static final Class Object_class  = Object.class;

    //########################################################################
    // Private Fields

    private final Definitions definitions;
    private final ClassLoader loader;

    private final Map/*<Class ,Class      >*/ arrays;
    private final Map/*<Symbol,Class      >*/ classes;
    private final Map/*<Symbol,Field      >*/ fields;
    private final Map/*<Symbol,Method     >*/ methods;
    private final Map/*<Symbol,Constructor>*/ constructors;

    //########################################################################
    // Public Constructors

    public JavaMirror(Definitions definitions, ClassLoader loader) {
        this.definitions = definitions;
        this.loader = loader;
        this.arrays = new HashMap();
        this.classes = new HashMap();
        this.fields = new HashMap();
        this.methods = new HashMap();
        this.constructors = new HashMap();
        this.classes.put(definitions.ANY_CLASS, Object_class);
        this.classes.put(definitions.ANYREF_CLASS, Object_class);
    }

    //########################################################################
    // Public Methods - arrays

    public Class getArray(Class component) {
        if (Proxy.isProxyClass(component)) component = Object_class;
        Object value = arrays.get(component);
        if (value != null) return (Class)value;
        Class array = getArray0(component);
        arrays.put(component, array);
        return array;
    }

    private Class getArray0(Class component) {
        String classname = "[" + getArrayComponentName(component);
        try {
            return Class.forName(classname, false, loader);
        } catch (ClassNotFoundException exception) {
            throw Debug.abort("no such class", classname);
        }
    }

    private String getArrayComponentName(Class component) {
        if (component.isPrimitive()) {
            if (component == boolean_class) return "Z";
            if (component == byte_class) return "B";
            if (component == short_class) return "S";
            if (component == char_class) return "C";
            if (component == int_class) return "I";
            if (component == long_class) return "J";
            if (component == float_class) return "F";
            if (component == double_class) return "D";
            throw Debug.abort("unknown primitive class", component);
        }
        String classname = component.getName();
        return component.isArray() ? classname : "L" + classname + ";";
    }

    //########################################################################
    // Public Methods - classes

    public Class getClass(Type type) {
        Type unboxed = type.unbox();
        if (unboxed != type) return getClass(unboxed);
        if (type instanceof Type.UnboxedType) {
            return getClass(((Type.UnboxedType)type).tag);
        }
        if (type instanceof Type.UnboxedArrayType) {
            return getArray(getClass(((Type.UnboxedArrayType)type).elemtp));
        }
        if (type instanceof Type.TypeRef) {
            return getClass(((Type.TypeRef)type).sym);
        }
        throw Debug.abort("illegal type", type);
    }

    public Class getClass(int kind) {
        switch (kind) {
        case TypeTags.UNIT   : return void_class;
        case TypeTags.BOOLEAN: return boolean_class;
        case TypeTags.BYTE   : return byte_class;
        case TypeTags.SHORT  : return short_class;
        case TypeTags.CHAR   : return char_class;
        case TypeTags.INT    : return int_class;
        case TypeTags.LONG   : return long_class;
        case TypeTags.FLOAT  : return float_class;
        case TypeTags.DOUBLE : return double_class;
        default              : throw Debug.abort("kind = " + kind);
        }
    }

    public Class getClass(Symbol symbol) {
        Object value = classes.get(symbol);
        if (value != null) return (Class)value;
        Class mirror = getClass0(symbol);
        assert Debug.log("java mirror: ", symbol, " -> ", mirror);
        classes.put(symbol, mirror);
        return mirror;
    }

    private Class getClass0(Symbol symbol) {
        String name = getClassName(symbol, false);
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException exception) {
            throw Debug.abort("no such class", Debug.show(symbol, name));
        }
    }

    private String getClassName(Symbol symbol, boolean asPrefix) {
        assert symbol.kind == Kinds.CLASS : Debug.show(symbol);
        String name = getPrefix(symbol.owner()) + symbol.name;
        if (!asPrefix && !symbol.isJava() && symbol.isModuleClass())
            name = name + '$';
        return name;
    }

    private String getPrefix(Symbol symbol) {
        assert symbol.kind == Kinds.CLASS : Debug.show(symbol);
        if (symbol.isRoot()) return "";
        String prefix = getClassName(symbol, true);
        return prefix + (symbol.isClass() ? '$' : '.');
    }

    //########################################################################
    // Public Methods - fields

    public Field getField(Symbol symbol) {
        Object value = fields.get(symbol);
        if (value != null) return (Field)value;
        Field mirror = getField0(symbol);
        assert Debug.log("java mirror: ", symbol, " -> ", mirror);
        fields.put(symbol, mirror);
        return mirror;
    }

    private Field getField0(Symbol symbol) {
        if (symbol.isModule()) {
            assert !symbol.isJava() : Debug.show(symbol);
            Class owner = getClass0(symbol.moduleClass());
            return getField0(symbol, owner, "MODULE$");
        } else {
            Class owner = getClass(symbol.owner());
            return getField0(symbol, owner, symbol.name.toString());
        }
    }

    private Field getField0(Symbol symbol, Class owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException exception) {
            throw Debug.abort("no such field", symbol);
        }
    }

    //########################################################################
    // Public Methods - methods

    public Method getMethod(Symbol symbol) {
        Object value = methods.get(symbol);
        if (value != null) return (Method)value;
        Method mirror = getMethod0(symbol);
        assert Debug.log("java mirror: ", symbol, " -> ", mirror);
        methods.put(symbol, mirror);
        return mirror;
    }

    private Method getMethod0(Symbol symbol) {
        Class owner = getClass(symbol.owner());
        Class[] params = getVParamsOf(symbol.type());
        try {
            return owner.getMethod(symbol.name.toString(), params);
        } catch (NoSuchMethodException exception) {
            Method fallback = getMethodByEquivalentParams(owner, symbol);
            if (fallback != null) return fallback;
            throw Debug.abort("no such method", symbol);
        }
    }

    private Method getMethodByEquivalentParams(Class owner, Symbol symbol) {
        Type[] params = getValueParamTypes(symbol.type());
        Method result = null;
        Method[] methods = owner.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().equals(symbol.name.toString())) continue;
            Class[] javaParams = method.getParameterTypes();
            if (javaParams.length != params.length) continue;
            int j = 0;
            while (j < javaParams.length
                && isEquivalentRuntimeClass(params[j], javaParams[j])) {
                j++;
            }
            if (j != javaParams.length) continue;
            if (result != null) throw Debug.abort("ambiguous method", symbol);
            result = method;
        }
        return result;
    }

    //########################################################################
    // Public Methods - constructors

    public Constructor getConstructor(Symbol symbol) {
        Object value = constructors.get(symbol);
        if (value != null) return (Constructor)value;
        Constructor mirror = getConstructor0(symbol);
        assert Debug.log("java mirror: ", symbol, " -> ", mirror);
        constructors.put(symbol, mirror);
        return mirror;
    }

    private Constructor getConstructor0(Symbol symbol) {
        Class owner = getClass(symbol.owner());
        Class[] params = getVParamsOf(symbol.type());
        try {
            return owner.getConstructor(params);
        } catch (NoSuchMethodException exception) {
            throw Debug.abort("no such constructor", symbol);
        }
    }

    //########################################################################
    // Public Methods - value parameters

    public Class[] getVParamsOf(Symbol symbol) {
        return getVParamsOf(symbol.type());
    }

    public Class[] getVParamsOf(Type type) {
        type = type.fullErasure();
        if (type instanceof Type.MethodType) {
            return getVParams(((Type.MethodType)type).vparams);
        }
        throw Debug.abort("illegal type", type);
    }

    public Class[] getVParams(Symbol[] symbols) {
        Class[] vparams = new Class[symbols.length];
        for (int i = 0; i < vparams.length; i++) {
            vparams[i] = getClass(symbols[i].type());
        }
        return vparams;
    }

    private Type[] getValueParamTypes(Type type) {
        if (type instanceof Type.PolyType) {
            return getValueParamTypes(((Type.PolyType)type).result);
        }
        if (type instanceof Type.MethodType) {
            return Symbol.type(((Type.MethodType)type).vparams);
        }
        throw Debug.abort("illegal method type", type);
    }

    private boolean isEquivalentRuntimeClass(Type type, Class clazz) {
        if (type instanceof Type.PolyType) {
            return isEquivalentRuntimeClass(((Type.PolyType)type).result, clazz);
        }
        if (type instanceof Type.UnboxedType) {
            return clazz == getClass(((Type.UnboxedType)type).tag)
                || clazz == getBoxedValueClass(((Type.UnboxedType)type).tag);
        }
        if (type instanceof Type.UnboxedArrayType) {
            return clazz.isArray()
                && isEquivalentRuntimeClass(
                    ((Type.UnboxedArrayType)type).elemtp,
                    clazz.getComponentType());
        }
        if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            if (typeRef.sym == definitions.ARRAY_CLASS && typeRef.args.length == 1) {
                return clazz.isArray()
                    && isEquivalentRuntimeClass(typeRef.args[0], clazz.getComponentType());
            }
            if (isScalaValueClass(typeRef.sym)) {
                return clazz == getClass(typeRef.sym)
                    || clazz == getPrimitiveValueClass(typeRef.sym);
            }
            return clazz == getClass(typeRef.sym);
        }
        return false;
    }

    private boolean isScalaValueClass(Symbol symbol) {
        return symbol == definitions.UNIT_CLASS
            || symbol == definitions.BOOLEAN_CLASS
            || symbol == definitions.BYTE_CLASS
            || symbol == definitions.SHORT_CLASS
            || symbol == definitions.CHAR_CLASS
            || symbol == definitions.INT_CLASS
            || symbol == definitions.LONG_CLASS
            || symbol == definitions.FLOAT_CLASS
            || symbol == definitions.DOUBLE_CLASS;
    }

    private Class getPrimitiveValueClass(Symbol symbol) {
        if (symbol == definitions.UNIT_CLASS) return void_class;
        if (symbol == definitions.BOOLEAN_CLASS) return boolean_class;
        if (symbol == definitions.BYTE_CLASS) return byte_class;
        if (symbol == definitions.SHORT_CLASS) return short_class;
        if (symbol == definitions.CHAR_CLASS) return char_class;
        if (symbol == definitions.INT_CLASS) return int_class;
        if (symbol == definitions.LONG_CLASS) return long_class;
        if (symbol == definitions.FLOAT_CLASS) return float_class;
        if (symbol == definitions.DOUBLE_CLASS) return double_class;
        throw Debug.abort("illegal value class", symbol);
    }

    private Class getBoxedValueClass(int kind) {
        switch (kind) {
        case TypeTags.BYTE: return getClass(definitions.BYTE_CLASS);
        case TypeTags.SHORT: return getClass(definitions.SHORT_CLASS);
        case TypeTags.CHAR: return getClass(definitions.CHAR_CLASS);
        case TypeTags.INT: return getClass(definitions.INT_CLASS);
        case TypeTags.LONG: return getClass(definitions.LONG_CLASS);
        case TypeTags.FLOAT: return getClass(definitions.FLOAT_CLASS);
        case TypeTags.DOUBLE: return getClass(definitions.DOUBLE_CLASS);
        case TypeTags.BOOLEAN: return getClass(definitions.BOOLEAN_CLASS);
        case TypeTags.UNIT: return getClass(definitions.UNIT_CLASS);
        default: throw Debug.abort("illegal value class kind", new Integer(kind));
        }
    }

    //########################################################################
}
