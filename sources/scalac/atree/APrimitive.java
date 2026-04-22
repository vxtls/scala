/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a primitive operation. */
public abstract class APrimitive {

    //########################################################################
    // Public Cases

    // type : (type) => type
    // range: type <- { BOOL, Ix, Ux, Rx }
    // jvm  : {i, l, f, d}neg
    public static final class Negation extends APrimitive {
        public final ATypeKind kind;

        private Negation(ATypeKind kind) {
            this.kind = kind;
        }
    }

    // type : zero ? (type) => BOOL : (type,type) => BOOL
    // range: type <- { BOOL, Ix, Ux, Rx, REF }
    // jvm  : if{eq, ne, lt, ge, le, gt}, if{null, nonnull}
    //        if_icmp{eq, ne, lt, ge, le, gt}, if_acmp{eq,ne}
    public static final class Test extends APrimitive {
        public final ATestOp op;
        public final ATypeKind kind;
        public final boolean zero;

        private Test(ATestOp op, ATypeKind kind, boolean zero) {
            this.op = op;
            this.kind = kind;
            this.zero = zero;
        }
    }

    // type : (type,type) => I4
    // range: type <- { Ix, Ux, Rx }
    // jvm  : lcmp, {f, d}cmp{l, g}
    public static final class Comparison extends APrimitive {
        public final AComparisonOp op;
        public final ATypeKind kind;

        private Comparison(AComparisonOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    // type : (type,type) => type
    // range: type <- { Ix, Ux, Rx }
    // jvm  : {i, l, f, d}{add, sub, mul, div, rem}
    public static final class Arithmetic extends APrimitive {
        public final AArithmeticOp op;
        public final ATypeKind kind;

        private Arithmetic(AArithmeticOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    // type : (type,type) => type
    // range: type <- { BOOL, Ix, Ux }
    // jvm  : {i, l}{and, or, xor}
    public static final class Logical extends APrimitive {
        public final ALogicalOp op;
        public final ATypeKind kind;

        private Logical(ALogicalOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    // type : (type,I4) => type
    // range: type <- { Ix, Ux }
    // jvm  : {i, l}{shl, ushl, shr}
    public static final class Shift extends APrimitive {
        public final AShiftOp op;
        public final ATypeKind kind;

        private Shift(AShiftOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    // type : (src) => dst
    // range: src,dst <- { Ix, Ux, Rx }
    // jvm  : i2{l, f, d}, l2{i, f, d}, f2{i, l, d}, d2{i, l, f}, i2{b, c, s}
    public static final class Conversion extends APrimitive {
        public final ATypeKind src;
        public final ATypeKind dst;

        private Conversion(ATypeKind src, ATypeKind dst) {
            this.src = src;
            this.dst = dst;
        }
    }

    // type : (Array[REF]) => I4
    // range: type <- { BOOL, Ix, Ux, Rx, REF }
    // jvm  : arraylength
    public static final class ArrayLength extends APrimitive {
        public final ATypeKind kind;

        private ArrayLength(ATypeKind kind) {
            this.kind = kind;
        }
    }

    // type : (lf,rg) => STR
    // range: lf,rg <- { BOOL, Ix, Ux, Rx, REF, STR }
    // jvm  : -
    public static final class StringConcat extends APrimitive {
        public final ATypeKind lf;
        public final ATypeKind rg;

        private StringConcat(ATypeKind lf, ATypeKind rg) {
            this.lf = lf;
            this.rg = rg;
        }
    }

    public static Negation Negation(ATypeKind kind) {
        return new Negation(kind);
    }

    public static Test Test(ATestOp op, ATypeKind kind, boolean zero) {
        return new Test(op, kind, zero);
    }

    public static Comparison Comparison(AComparisonOp op, ATypeKind kind) {
        return new Comparison(op, kind);
    }

    public static Arithmetic Arithmetic(AArithmeticOp op, ATypeKind kind) {
        return new Arithmetic(op, kind);
    }

    public static Logical Logical(ALogicalOp op, ATypeKind kind) {
        return new Logical(op, kind);
    }

    public static Shift Shift(AShiftOp op, ATypeKind kind) {
        return new Shift(op, kind);
    }

    public static Conversion Conversion(ATypeKind src, ATypeKind dst) {
        return new Conversion(src, dst);
    }

    public static ArrayLength ArrayLength(ATypeKind kind) {
        return new ArrayLength(kind);
    }

    public static StringConcat StringConcat(ATypeKind lf, ATypeKind rg) {
        return new StringConcat(lf, rg);
    }

    //########################################################################
    // Public Methods

    /** Returns a string representation of this primitive. */
    public String toString() {
        return new ATreePrinter().printPrimitive(this).toString();
    }

    //########################################################################
}
