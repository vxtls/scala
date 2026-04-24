/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a primitive operation. */
public class APrimitive {

    //########################################################################
    // Public Cases

    public static final class Negation extends APrimitive {
        public final ATypeKind kind;

        public Negation(ATypeKind kind) {
            this.kind = kind;
        }
    }

    public static final class Test extends APrimitive {
        public final ATestOp op;
        public final ATypeKind kind;
        public final boolean zero;

        public Test(ATestOp op, ATypeKind kind, boolean zero) {
            this.op = op;
            this.kind = kind;
            this.zero = zero;
        }
    }

    public static final class Comparison extends APrimitive {
        public final AComparisonOp op;
        public final ATypeKind kind;

        public Comparison(AComparisonOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    public static final class Arithmetic extends APrimitive {
        public final AArithmeticOp op;
        public final ATypeKind kind;

        public Arithmetic(AArithmeticOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    public static final class Logical extends APrimitive {
        public final ALogicalOp op;
        public final ATypeKind kind;

        public Logical(ALogicalOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    public static final class Shift extends APrimitive {
        public final AShiftOp op;
        public final ATypeKind kind;

        public Shift(AShiftOp op, ATypeKind kind) {
            this.op = op;
            this.kind = kind;
        }
    }

    public static final class Conversion extends APrimitive {
        public final ATypeKind src;
        public final ATypeKind dst;

        public Conversion(ATypeKind src, ATypeKind dst) {
            this.src = src;
            this.dst = dst;
        }
    }

    public static final class ArrayLength extends APrimitive {
        public final ATypeKind kind;

        public ArrayLength(ATypeKind kind) {
            this.kind = kind;
        }
    }

    public static final class StringConcat extends APrimitive {
        public final ATypeKind lf;
        public final ATypeKind rg;

        public StringConcat(ATypeKind lf, ATypeKind rg) {
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
