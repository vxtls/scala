/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.symtab.Symbol;
import scalac.symtab.Type;

/** This class represents attributed code. */
public abstract class ACode {

    //########################################################################
    // Public Cases

    private static final class VoidCode extends ACode {
    }

    // jvm  : -
    public static final ACode Void = new VoidCode();

    // jvm  : aload_0
    public static final class This extends ACode {
        public final Symbol clasz;

        private This(Symbol clasz) {
            this.clasz = clasz;
        }
    }

    // jvm  : {b, s}ipush, ldc{ ,_w, 2_w}, aconst_null
    // jvm  : iconst_{m1, 2, 3, 4, 5}, {i, l, f, d}const_{0, 1}, fconst_2
    public static final class Constant extends ACode {
        public final AConstant constant;

        private Constant(AConstant constant) {
            this.constant = constant;
        }
    }

    // jvm  : get{static, field}
    // jvm  : {i, l, f, d, a}load{, _0, _1, _2, _3}
    // jvm  : {i, l, f, d, a, b, c, s}aload
    public static final class Load extends ACode {
        public final ALocation location;

        private Load(ALocation location) {
            this.location = location;
        }
    }

    // jvm  : put{static, field}
    // jvm  : {i, l, f, d, a}store{, _0, _1, _2, _3}
    // jvm  : {i, l, f, d, a, b, c, s}store
    public static final class Store extends ACode {
        public final ALocation location;
        public final ACode value;

        private Store(ALocation location, ACode value) {
            this.location = location;
            this.value = value;
        }
    }

    // jvm  : new, invoke{static, virtual, interface, special}, {, a}newarray
    // jvm  : <see also in APrimitive>
    public static final class Apply extends ACode {
        public final AFunction function;
        public final Type[] targs;
        public final ACode[] vargs;

        private Apply(AFunction function, Type[] targs, ACode[] vargs) {
            this.function = function;
            this.targs = targs;
            this.vargs = vargs;
        }
    }

    // jvm  : instanceof, checkcast
    public static final class IsAs extends ACode {
        public final ACode value;
        public final Type type;
        public final boolean cast;

        private IsAs(ACode value, Type type, boolean cast) {
            this.value = value;
            this.type = type;
            this.cast = cast;
        }
    }

    // jvm  : -
    public static final class If extends ACode {
        public final ACode test;
        public final ACode success;
        public final ACode failure;

        private If(ACode test, ACode success, ACode failure) {
            this.test = test;
            this.success = success;
            this.failure = failure;
        }
    }

    // jvm  : {tables, lookup}switch
    public static final class Switch extends ACode {
        public final ACode test;
        public final int[][] tags;
        public final ACode[] bodies;

        private Switch(ACode test, int[][] tags, ACode[] bodies) {
            this.test = test;
            this.tags = tags;
            this.bodies = bodies;
        }
    }

    // jvm  : monitor{enter, exit}
    public static final class Synchronized extends ACode {
        public final ACode lock;
        public final ACode value;

        private Synchronized(ACode lock, ACode value) {
            this.lock = lock;
            this.value = value;
        }
    }

    // jvm  : -
    public static final class Block extends ACode {
        public final Symbol[] locals;
        public final ACode[] statements;
        public final ACode value;

        private Block(Symbol[] locals, ACode[] statements, ACode value) {
            this.locals = locals;
            this.statements = statements;
            this.value = value;
        }
    }

    // jvm  : -
    public static final class Label extends ACode {
        public final Symbol label;
        public final Symbol[] locals;
        public final ACode value;

        private Label(Symbol label, Symbol[] locals, ACode value) {
            this.label = label;
            this.locals = locals;
            this.value = value;
        }
    }

    // jvm  : goto, goto_w
    public static final class Goto extends ACode {
        public final Symbol label;
        public final ACode[] vargs;

        private Goto(Symbol label, ACode[] vargs) {
            this.label = label;
            this.vargs = vargs;
        }
    }

    // jvm  : {i, l, f, d, a, }return
    public static final class Return extends ACode {
        public final Symbol function;
        public final ACode value;

        private Return(Symbol function, ACode value) {
            this.function = function;
            this.value = value;
        }
    }

    // jvm  : athrow
    public static final class Throw extends ACode {
        public final ACode value;

        private Throw(ACode value) {
            this.value = value;
        }
    }

    // jvm  : pop, pop2
    public static final class Drop extends ACode {
        public final ACode value;
        public final Type type;

        private Drop(ACode value, Type type) {
            this.value = value;
            this.type = type;
        }
    }

    public static This This(Symbol clasz) {
        return new This(clasz);
    }

    public static Constant Constant(AConstant constant) {
        return new Constant(constant);
    }

    public static Load Load(ALocation location) {
        return new Load(location);
    }

    public static Store Store(ALocation location, ACode value) {
        return new Store(location, value);
    }

    public static Apply Apply(AFunction function, Type[] targs, ACode[] vargs) {
        return new Apply(function, targs, vargs);
    }

    public static IsAs IsAs(ACode value, Type type, boolean cast) {
        return new IsAs(value, type, cast);
    }

    public static If If(ACode test, ACode success, ACode failure) {
        return new If(test, success, failure);
    }

    public static Switch Switch(ACode test, int[][] tags, ACode[] bodies) {
        return new Switch(test, tags, bodies);
    }

    public static Synchronized Synchronized(ACode lock, ACode value) {
        return new Synchronized(lock, value);
    }

    public static Block Block(Symbol[] locals, ACode[] statements, ACode value) {
        return new Block(locals, statements, value);
    }

    public static Label Label(Symbol label, Symbol[] locals, ACode value) {
        return new Label(label, locals, value);
    }

    public static Goto Goto(Symbol label, ACode[] vargs) {
        return new Goto(label, vargs);
    }

    public static Return Return(Symbol function, ACode value) {
        return new Return(function, value);
    }

    public static Throw Throw(ACode value) {
        return new Throw(value);
    }

    public static Drop Drop(ACode value, Type type) {
        return new Drop(value, type);
    }

    // jvm  : nop, dup{, _x1, _x2, 2, 2_x1, 2_x2}, swap
    // jvm  : multianewarray, iinc, jsr{, _w}, ret, wide
    // NOT MAPPED

    //########################################################################
    // Public Fields

    /** The source file position */
    public int pos;

    //########################################################################
    // Public Methods

    /** Returns a string representation of this code. */
    public String toString() {
        return new ATreePrinter().printCode(this).toString();
    }

    //########################################################################
}
