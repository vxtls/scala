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
public class ACode {

    //########################################################################
    // Public Constants

    public static final ACode[] EMPTY_ARRAY = new ACode[0];

    //########################################################################
    // Public Cases

    public static final class Void extends ACode {
        public Void() {
        }
    }

    public static final ACode Void = new Void();

    public static final class This extends ACode {
        public final Symbol clasz;

        public This(Symbol clasz) {
            this.clasz = clasz;
        }
    }

    public static final class Constant extends ACode {
        public final AConstant constant;

        public Constant(AConstant constant) {
            this.constant = constant;
        }
    }

    public static final class Load extends ACode {
        public final ALocation location;

        public Load(ALocation location) {
            this.location = location;
        }
    }

    public static final class Store extends ACode {
        public final ALocation location;
        public final ACode value;

        public Store(ALocation location, ACode value) {
            this.location = location;
            this.value = value;
        }
    }

    public static final class Apply extends ACode {
        public final AFunction function;
        public final Type[] targs;
        public final ACode[] vargs;

        public Apply(AFunction function, Type[] targs, ACode[] vargs) {
            this.function = function;
            this.targs = targs;
            this.vargs = vargs;
        }
    }

    public static final class IsAs extends ACode {
        public final ACode value;
        public final Type type;
        public final boolean cast;

        public IsAs(ACode value, Type type, boolean cast) {
            this.value = value;
            this.type = type;
            this.cast = cast;
        }
    }

    public static final class If extends ACode {
        public final ACode test;
        public final ACode success;
        public final ACode failure;

        public If(ACode test, ACode success, ACode failure) {
            this.test = test;
            this.success = success;
            this.failure = failure;
        }
    }

    public static final class Switch extends ACode {
        public final ACode test;
        public final int[][] tags;
        public final ACode[] bodies;

        public Switch(ACode test, int[][] tags, ACode[] bodies) {
            this.test = test;
            this.tags = tags;
            this.bodies = bodies;
        }
    }

    public static final class Synchronized extends ACode {
        public final ACode lock;
        public final ACode value;

        public Synchronized(ACode lock, ACode value) {
            this.lock = lock;
            this.value = value;
        }
    }

    public static final class Block extends ACode {
        public final Symbol[] locals;
        public final ACode[] statements;
        public final ACode value;

        public Block(Symbol[] locals, ACode[] statements, ACode value) {
            this.locals = locals;
            this.statements = statements;
            this.value = value;
        }
    }

    public static final class Label extends ACode {
        public final Symbol label;
        public final Symbol[] locals;
        public final ACode value;

        public Label(Symbol label, Symbol[] locals, ACode value) {
            this.label = label;
            this.locals = locals;
            this.value = value;
        }
    }

    public static final class Goto extends ACode {
        public final Symbol label;
        public final ACode[] vargs;

        public Goto(Symbol label, ACode[] vargs) {
            this.label = label;
            this.vargs = vargs;
        }
    }

    public static final class Return extends ACode {
        public final Symbol function;
        public final ACode value;

        public Return(Symbol function, ACode value) {
            this.function = function;
            this.value = value;
        }
    }

    public static final class Throw extends ACode {
        public final ACode value;

        public Throw(ACode value) {
            this.value = value;
        }
    }

    public static final class Drop extends ACode {
        public final ACode value;
        public final Type type;

        public Drop(ACode value, Type type) {
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
