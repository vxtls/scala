/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Evaluator.java,v 1.40 2002/10/04 15:37:10 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import scala.tools.util.Position;
import scala.tools.util.SourceFile;

import scala.runtime.RunTime;

import scalac.symtab.Symbol;
import scalac.symtab.SymbolNameWriter;
import scalac.symtab.Type;
import scalac.util.Debug;
import scalac.util.Names;

public class Evaluator {

    //########################################################################
    // Private Classes

    // !!! remove ?
    // !!! is it correct to extend EvaluatorException?
    private static class LabelException extends EvaluatorException {

        public final Symbol symbol;
        public final Object[] args;

        public LabelException(Symbol symbol, Object[] args) {
            this.symbol = symbol;
            this.args = args;
        }
    }

    public static class EvaluationStack {

        public final EvaluationStack stack;
        public final SourceFile source;
        public final Symbol symbol;
        public final Object self;
        public final Object[] args;
        public final Object[] vars;

        public int pos;

        public EvaluationStack(EvaluationStack stack, CodeContainer code,
            Object self, Object[] args)
        {
            this.stack = stack;
            this.source = code.source;
            this.symbol = code.symbol;
            this.self = self;
            this.args = args;
            this.vars = new Object[code.stacksize];
        }

    }

    //########################################################################
    // Private Fields

    private final Map/*<Class,Set<ScalaTemplate>>*/ templates;
    private final EvaluatorException trace;
    private EvaluationStack stack;

    //########################################################################
    // Public Constructors

    public Evaluator(Map templates) {
        this.templates = templates;
        this.trace = new EvaluatorException();
        this.stack = null;
    }

    //########################################################################
    // Public Methods

    public Object toString(Object object) {
        try {
            return String.valueOf(object);
        } catch (Throwable exception) {
            return throw_(exception);
        }
    }

    public Object evaluate(Variable module) {
        return load(null, module);
    }

    public Object evaluate(Variable module, Function method, Object[] args) {
        return invoke(load(null, module), method, args);
    }

    public Object evaluate(CodeContainer code) {
        return evaluate(code, null, new Object[0]);
    }

    public Object evaluate(CodePromise code, Object object, Object[] args) {
        return evaluate(code.force(), object, args);
    }

    public Object evaluate(CodeContainer code, Object object, Object[] args) {
        return trace(code, object, args);
    }

    //########################################################################
    // Private Methods - trace

    private Object trace(CodeContainer code, Object object, Object[] args) {
        EvaluationStack previous = stack;
        EvaluationStack current =
            new EvaluationStack(previous, code, object, normalizeValues(args));
        try {
            try {
                stack = current;
                return evaluate(code.code);
            } catch (EvaluatorException exception) {
                throw exception;
            } catch (Throwable exception) {
                return throw_(exception, "trace");
            }
        } catch (EvaluatorException exception) {
            StringBuffer buffer = new StringBuffer();
            buffer.append(getClassName(current.symbol.owner()));
            buffer.append('.');
            buffer.append(current.symbol.nameString());
            buffer.append('(');
            buffer.append(current.source.getFile().getName());
            int line = Position.line(current.pos);
            if (line != 0) buffer.append(':').append(line);
            buffer.append(")");
            exception.addScalaCall(buffer.toString());
            throw exception;
        } finally {
            stack = previous;
        }
    }

    //########################################################################
    // Private Methods - evaluate

    private Object evaluate(Code code) {
        if (code instanceof Code.Block) {
            Code.Block block = (Code.Block)code;
            for (int i = 0; i < block.stats.length; i++) evaluate(block.stats[i]);
            return evaluate(block.value);
        }
        if (code instanceof Code.Label) {
            Code.Label label = (Code.Label)code;
            while (true)
                try {
                    return evaluate(label.expression);
                } catch (LabelException exception) {
                    if (exception.symbol != label.symbol) throw exception;
                    for (int i = 0; i < label.variables.length; i++) {
                        // !!! null
                        store(null, label.variables[i], exception.args[i]);
                    }
                }
        }
        if (code instanceof Code.Synchronized) {
            Code.Synchronized sync = (Code.Synchronized)code;
            Object value = evaluate(sync.object);
            synchronized (value) { return evaluate(sync.expression); }
        }
        if (code instanceof Code.If) {
            Code.If branch = (Code.If)code;
            Object value = evaluate(branch.cond);
            return evaluate(booleanValue(value) ? branch.thenp : branch.elsep);
        }
        if (code instanceof Code.Switch) {
            Code.Switch switchCode = (Code.Switch)code;
            Object value = evaluate(switchCode.test);
            int tag = intValue(value);
            for (int i = 0; i < switchCode.tags.length; i++)
                if (switchCode.tags[i] == tag) return evaluate(switchCode.bodies[i]);
            return evaluate(switchCode.otherwise);
        }
        if (code instanceof Code.Literal) {
            return normalizeValue(((Code.Literal)code).value);
        }
        if (code instanceof Code.Load) {
            Code.Load load = (Code.Load)code;
            return load(evaluate(load.target), load.variable);
        }
        if (code instanceof Code.Store) {
            Code.Store store = (Code.Store)code;
            store(evaluate(store.target), store.variable, evaluate(store.expression));
            return RunTime.box_uvalue();
        }
        if (code instanceof Code.Invoke) {
            Code.Invoke invoke = (Code.Invoke)code;
            Object object = evaluate(invoke.target);
            Object[] args = new Object[invoke.arguments.length];
            for (int i = 0; i < args.length; i++)
                args[i] = evaluate(invoke.arguments[i]);
            stack.pos = invoke.pos;
            return invoke(object, invoke.function, args);
        }
        if (code instanceof Code.Create) {
            ScalaTemplate template = ((Code.Create)code).template;
            return invoke(null, template.getConstructor(),
                new Object[] {template.getHandler()});
        }
        if (code instanceof Code.CreateArray) {
            Code.CreateArray createArray = (Code.CreateArray)code;
            Object length = evaluate(createArray.size);
            return Array.newInstance(createArray.component, intValue(length));
        }
        if (code instanceof Code.IsAs) {
            Code.IsAs isAs = (Code.IsAs)code;
            Object object = evaluate(isAs.target);
            if (object == null) return isAs.cast ? null : Boolean.FALSE;
            boolean test = isInstanceOf(object, isAs.type, isAs.base);
            return isAs.cast
                ? (test ? object : throw_(getCastException(object, isAs.type)))
                : (test ? Boolean.TRUE : Boolean.FALSE);
        }
        if (code instanceof Code.Or) {
            Code.Or or = (Code.Or)code;
            Object object = evaluate(or.lf);
            boolean value = booleanValue(object);
            if (value) return new Boolean(value);
            return evaluate(or.rg);
        }
        if (code instanceof Code.And) {
            Code.And and = (Code.And)code;
            Object object = evaluate(and.lf);
            boolean value = booleanValue(object);
            if (!value) return new Boolean(value);
            return evaluate(and.rg);
        }
        if (code == Code.Null) {
            return null;
        }
        if (code == Code.Self) {
            return stack.self;
        }
        throw Debug.abort("illegal code", code);
    }

    //########################################################################
    // Private Methods - invoke

    private boolean booleanValue(Object object) {
        if (object instanceof java.lang.Boolean)
            return ((java.lang.Boolean)object).booleanValue();
        if (object instanceof scala.Boolean)
            return ((scala.Boolean)object).value;
        throw Debug.abort("not a boolean value", object);
    }

    private int intValue(Object object) {
        if (object instanceof java.lang.Integer)
            return ((java.lang.Integer)object).intValue();
        if (object instanceof scala.Int)
            return ((scala.Int)object).value;
        throw Debug.abort("not an int value", object);
    }

    private Object[] normalizeValues(Object[] values) {
        if (values == null)
            return new Object[0];
        Object[] normalized = null;
        for (int i = 0; i < values.length; i++) {
            Object value = normalizeValue(values[i]);
            if (value != values[i] && normalized == null) {
                normalized = new Object[values.length];
                System.arraycopy(values, 0, normalized, 0, values.length);
            }
            if (normalized != null)
                normalized[i] = value;
        }
        return normalized == null ? values : normalized;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof java.lang.Boolean)
            return RunTime.box_zvalue(((java.lang.Boolean)value).booleanValue());
        if (value instanceof java.lang.Byte)
            return RunTime.box_bvalue(((java.lang.Byte)value).byteValue());
        if (value instanceof java.lang.Short)
            return RunTime.box_svalue(((java.lang.Short)value).shortValue());
        if (value instanceof java.lang.Character)
            return RunTime.box_cvalue(((java.lang.Character)value).charValue());
        if (value instanceof java.lang.Integer)
            return RunTime.box_ivalue(((java.lang.Integer)value).intValue());
        if (value instanceof java.lang.Long)
            return RunTime.box_lvalue(((java.lang.Long)value).longValue());
        if (value instanceof java.lang.Float)
            return RunTime.box_fvalue(((java.lang.Float)value).floatValue());
        if (value instanceof java.lang.Double)
            return RunTime.box_dvalue(((java.lang.Double)value).doubleValue());
        return value;
    }

    private Object adaptReceiver(Method method, Object object) {
        return adaptArgument(method.getDeclaringClass(), object);
    }

    private Object[] adaptArguments(Method method, Object[] args) {
        Class[] params = method.getParameterTypes();
        assert params.length == args.length : Debug.show(method, args);
        Object[] adapted = null;
        for (int i = 0; i < args.length; i++) {
            Object arg = adaptArgument(params[i], args[i]);
            if (arg != args[i] && adapted == null) {
                adapted = new Object[args.length];
                System.arraycopy(args, 0, adapted, 0, args.length);
            }
            if (adapted != null)
                adapted[i] = arg;
        }
        return adapted == null ? args : adapted;
    }

    private Object adaptArgument(Class param, Object arg) {
        if (arg == null)
            return arg;

        if (param == scala.Boolean.class && arg instanceof java.lang.Boolean)
            return RunTime.box_zvalue(((java.lang.Boolean)arg).booleanValue());
        if (param == scala.Byte.class && arg instanceof java.lang.Byte)
            return RunTime.box_bvalue(((java.lang.Byte)arg).byteValue());
        if (param == scala.Short.class && arg instanceof java.lang.Short)
            return RunTime.box_svalue(((java.lang.Short)arg).shortValue());
        if (param == scala.Char.class && arg instanceof java.lang.Character)
            return RunTime.box_cvalue(((java.lang.Character)arg).charValue());
        if (param == scala.Int.class && arg instanceof java.lang.Integer)
            return RunTime.box_ivalue(((java.lang.Integer)arg).intValue());
        if (param == scala.Long.class && arg instanceof java.lang.Long)
            return RunTime.box_lvalue(((java.lang.Long)arg).longValue());
        if (param == scala.Float.class && arg instanceof java.lang.Float)
            return RunTime.box_fvalue(((java.lang.Float)arg).floatValue());
        if (param == scala.Double.class && arg instanceof java.lang.Double)
            return RunTime.box_dvalue(((java.lang.Double)arg).doubleValue());
        if (param == scala.Unit.class && arg instanceof scala.Unit)
            return arg;

        if ((param == java.lang.Boolean.TYPE || param == java.lang.Boolean.class)
            && arg instanceof scala.Boolean)
            return new java.lang.Boolean(((scala.Boolean)arg).value);
        if ((param == java.lang.Byte.TYPE || param == java.lang.Byte.class)
            && arg instanceof scala.Byte)
            return new java.lang.Byte(((scala.Byte)arg).value);
        if ((param == java.lang.Short.TYPE || param == java.lang.Short.class)
            && arg instanceof scala.Short)
            return new java.lang.Short(((scala.Short)arg).value);
        if ((param == java.lang.Character.TYPE || param == java.lang.Character.class)
            && arg instanceof scala.Char)
            return new java.lang.Character(((scala.Char)arg).value);
        if ((param == java.lang.Integer.TYPE || param == java.lang.Integer.class)
            && arg instanceof scala.Int)
            return new java.lang.Integer(((scala.Int)arg).value);
        if ((param == java.lang.Long.TYPE || param == java.lang.Long.class)
            && arg instanceof scala.Long)
            return new java.lang.Long(((scala.Long)arg).value);
        if ((param == java.lang.Float.TYPE || param == java.lang.Float.class)
            && arg instanceof scala.Float)
            return new java.lang.Float(((scala.Float)arg).value);
        if ((param == java.lang.Double.TYPE || param == java.lang.Double.class)
            && arg instanceof scala.Double)
            return new java.lang.Double(((scala.Double)arg).value);

        return arg;
    }

    public Object adaptResult(Method method, Object value) {
        if (method.getReturnType() == java.lang.Void.TYPE)
            return null;
        return adaptArgument(method.getReturnType(), value);
    }

    private Object invoke(Object object, Function function, Object[] args) {
        if (function instanceof Function.Global) {
            return evaluate(((Function.Global)function).code, object, args);
        }
        if (function instanceof Function.Member) {
            return getScalaObject(object).invoke(object, ((Function.Member)function).symbol, args);
        }
        if (function instanceof Function.Label) {
            throw new LabelException(((Function.Label)function).symbol, args);
        }
        if (function instanceof Function.JavaConstructor) {
            return invoke(object, ((Function.JavaConstructor)function).constructor, args);
        }
        if (function instanceof Function.JavaMethod) {
            return invoke(object, ((Function.JavaMethod)function).method, args);
        }
        if (function == Function.Pos) {
            if (object instanceof scala.Int) {
                int value = ((scala.Int)object).value;
                return new Integer(value);
            } else if (object instanceof scala.Long) {
                long value = ((scala.Long)object).value;
                return new Long(value);
            } else if (object instanceof scala.Float) {
                float value = ((scala.Float)object).value;
                return new Float(value);
            } else {
                double value = ((scala.Double)object).value;
                return new Double(value);
            }
        }
        if (function == Function.Neg) {
            if (object instanceof scala.Int) {
                int value = ((scala.Int)object).value;
                return new Integer(-value);
            } else if (object instanceof scala.Long) {
                long value = ((scala.Long)object).value;
                return new Long(-value);
            } else if (object instanceof scala.Float) {
                float value = ((scala.Float)object).value;
                return new Float(-value);
            } else {
                double value = ((scala.Double)object).value;
                return new Double(-value);
            }
        }
        if (function == Function.Throw) {
            assert args.length == 0 : Debug.show(args);
            assert object instanceof Throwable : object.getClass();
            return throw_((Throwable)object);
        }
        if (function == Function.StringPlus) {
            assert args.length == 1 : Debug.show(args);
            //assert object instanceof String : object.getClass().getName();
            return (String.valueOf(object)).concat(String.valueOf(args[0]));
        }
        if (function == Function.Eq) {
            assert args.length == 1 : Debug.show(args);
            return object == args[0] ? Boolean.TRUE : Boolean.FALSE;
        }
        if (function == Function.EqEq) {
            assert args.length == 1 : Debug.show(args);
            return object == null ? new Boolean(args[0] == null) : new Boolean(object.equals(args[0])); // !!!
        }
        if (function == Function.BangEq) {
            assert args.length == 1 : Debug.show(args);
            return object == null ? new Boolean(args[0] != null) : new Boolean(!object.equals(args[0])); // !!!
        }
        if (function == Function.HashCode) {
            assert args.length == 0 : Debug.show(args);
            return new Integer(getScalaObject(object).hashCode());
        }
        if (function == Function.ToString) {
            assert args.length == 0 : Debug.show(args);
            return getScalaObject(object).toString();
        }
        throw Debug.abort("illegal function", function);
    }

    private Object invoke(Object object, Constructor constructor,Object[]args){
        try {
            return constructor.newInstance(args);
        } catch (StackOverflowError exception) {
            return throw_(exception);
        } catch (ExceptionInInitializerError exception) {
            return throw_(exception);
        } catch (InvocationTargetException exception) {
            return throw_(exception);
        } catch (InstantiationException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  constr = " + Debug.show(constructor);
            String msg3 = "\n  args   = " + Debug.show(args);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        } catch (IllegalAccessException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  constr = " + Debug.show(constructor);
            String msg3 = "\n  args   = " + Debug.show(args);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        } catch (IllegalArgumentException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  constr = " + Debug.show(constructor);
            String msg3 = "\n  args   = " + Debug.show(args);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        }
    }

    private Object invoke(Object object, Method method, Object[] args) {
        try {
            return method.invoke(adaptReceiver(method, object), adaptArguments(method, args));
        } catch (StackOverflowError exception) {
            return throw_(exception);
        } catch (NullPointerException exception) {
            return throw_(exception);
        } catch (ExceptionInInitializerError exception) {
            return throw_(exception);
        } catch (InvocationTargetException exception) {
            return throw_(exception);
        } catch (IllegalAccessException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  method = " + Debug.show(method);
            String msg3 = "\n  args   = " + Debug.show(args);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        } catch (IllegalArgumentException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  method = " + Debug.show(method);
            String msg3 = "\n  args   = " + Debug.show(args);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        }
    }

    //########################################################################
    // Private Methods - store

    private Object store(Object object, Variable variable, Object value) {
        value = normalizeValue(value);
        if (variable instanceof Variable.Global) {
            return ((Variable.Global)variable).value = value;
        }
        if (variable instanceof Variable.Module) {
            return ((Variable.Module)variable).value = value;
        }
        if (variable instanceof Variable.Member) {
            int index = ((Variable.Member)variable).index;
            return getScalaObject(object).variables[index] = value;
        }
        if (variable instanceof Variable.Argument) {
            int index = ((Variable.Argument)variable).index;
            return stack.args[index] = value;
        }
        if (variable instanceof Variable.Local) {
            int index = ((Variable.Local)variable).index;
            return stack.vars[index] = value;
        }
        if (variable instanceof Variable.JavaField) {
            return store(object, ((Variable.JavaField)variable).field, value);
        }
        throw Debug.abort("illegal variable", variable);
    }

    private Object store(Object object, Field field, Object value) {
        try {
            value = adaptArgument(field.getType(), value);
            field.set(object, value);
            return value;
        } catch (NullPointerException exception) {
            return throw_(exception);
        } catch (ExceptionInInitializerError exception) {
            return throw_(exception);
        } catch (IllegalAccessException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  field  = " + Debug.show(field);
            String msg3 = "\n  value  = " + Debug.show(value);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        } catch (IllegalArgumentException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  field  = " + Debug.show(field);
            String msg3 = "\n  value  = " + Debug.show(value);
            throw Debug.abort(msg1 + msg2 + msg3, exception);
        }
    }

    //########################################################################
    // Private Methods - load

    private Object load(Object object, Variable variable) {
        if (variable instanceof Variable.Global) {
            return ((Variable.Global)variable).value;
        }
        if (variable instanceof Variable.Module) {
            Variable.Module module = (Variable.Module)variable;
            if (module.value != null) return module.value;
            ScalaTemplate template = module.template;
            Object value = evaluate(Code.Create(template));
            module.template = null;
            module.value = value;
            Symbol clasz = template.getSymbol();
            Symbol initializer = clasz.lookup(Names.INITIALIZER);
            CodePromise promise = template.getMethod(initializer);
            assert promise != null: Debug.show(clasz, initializer);
            evaluate(promise, value, new Object[0]);
            return value;
        }
        if (variable instanceof Variable.Member) {
            int index = ((Variable.Member)variable).index;
            return getScalaObject(object).variables[index];
        }
        if (variable instanceof Variable.Argument) {
            int index = ((Variable.Argument)variable).index;
            return stack.args[index];
        }
        if (variable instanceof Variable.Local) {
            int index = ((Variable.Local)variable).index;
            return stack.vars[index];
        }
        if (variable instanceof Variable.JavaField) {
            return load(object, ((Variable.JavaField)variable).field);
        }
        throw Debug.abort("illegal variable", variable);
    }

    private Object load(Object object, Field field) {
        try {
            return field.get(object);
        } catch (NullPointerException exception) {
            return throw_(exception);
        } catch (ExceptionInInitializerError exception) {
            return throw_(exception);
        } catch (IllegalAccessException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  field  = " + Debug.show(field);
            throw Debug.abort(msg1 + msg2, exception);
        } catch (IllegalArgumentException exception) {
            String msg1 = "\n  object = " + Debug.show(object);
            String msg2 = "\n  field  = " + Debug.show(field);
            throw Debug.abort(msg1 + msg2, exception);
        }
    }

    //########################################################################
    // Private Methods - throw

    private Object throw_(Throwable exception) {
        return throw_(exception, null);
    }

    private Object throw_(Throwable exception, String method) {
        if (exception.getCause() != null && (
                exception instanceof ExceptionInInitializerError ||
                exception instanceof InvocationTargetException))
            exception = exception.getCause();
        if (trace.getCause() != exception) trace.reset(exception);
        trace.addScalaLeavePoint(getClass().getName(), method);
        throw trace;
    }

    //########################################################################
    // Private Methods - !!!

    private ScalaObject getScalaObject(Object object) {
        assert object instanceof Proxy: object.getClass();
        Object handler = object == null
            ? throw_(new NullPointerException())
            : Proxy.getInvocationHandler(object);
        assert handler instanceof ScalaObject: handler.getClass();
        return (ScalaObject)handler;
    }

    private Symbol getScalaSymbol(Object object) {
        Class clasz = object.getClass();
        if (!(object instanceof Proxy)) return null;
        Object handler = Proxy.getInvocationHandler(object);
        if (!(handler instanceof ScalaObject)) return null;
        return ((ScalaObject)handler).template.getSymbol();
    }

    private boolean isInstanceOf(Object object, Type type, Class base) {
        Class clasz = object.getClass();
        if (type instanceof Type.TypeRef) {
            Symbol symbol = ((Type.TypeRef)type).sym;
            Symbol scala = getScalaSymbol(object);
            if (scala != null) return scala.isSubClass(symbol);
            return base.isAssignableFrom(clasz);
        }
        if (type instanceof Type.UnboxedArrayType) {
            return base.isAssignableFrom(clasz);
        }
        throw Debug.abort("illegal case", type);
    }

    private String getClassNameOf(Object object) {
        Symbol symbol = getScalaSymbol(object);
        if (symbol != null) getClassName(symbol);
        Class clasz = object.getClass();
        if (!clasz.isArray()) return clasz.getName();
        return getClassName(clasz);
    }

    // !!! public
    public String getClassName(Symbol clasz) {
        SymbolNameWriter writer = new SymbolNameWriter().setNameDecoding(true);
        return writer.toString(clasz);
    }

    private String getClassName(Class clasz) {
        if (clasz.isArray())
            return getClassName(clasz.getComponentType()) + "[]";
        Set scalas = (Set)templates.get(clasz);
        if (scalas == null) return clasz.getName();
        StringBuffer buffer = new StringBuffer();
        boolean separator = false;
        if (scalas.size() != 1) buffer.append('(');
        for (Iterator i = scalas.iterator(); i.hasNext(); separator = true) {
            if (separator) buffer.append(" | ");
            ScalaTemplate scala = (ScalaTemplate)i.next();
            buffer.append(getClassName(scala.getSymbol()));
        }
        if (scalas.size() != 1) buffer.append(')');
        return buffer.toString();
    }

    private String getClassName(Type type) {
        if (type instanceof Type.TypeRef) {
            return getClassName(((Type.TypeRef)type).sym);
        }
        if (type instanceof Type.UnboxedArrayType) {
            return getClassName(((Type.UnboxedArrayType)type).elemtp) + "[]";
        }
        if (type instanceof Type.UnboxedType) {
            return type.toString();
        }
        throw Debug.abort("illegal case");
    }

    private ClassCastException getCastException(Object object, Type type) {
        String from = "class " + getClassNameOf(object);
        String to = "class " + getClassName(type);
        return new ClassCastException(from + " is not an instance of " + to);
    }

    //########################################################################
}
