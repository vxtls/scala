/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a constant. */
public abstract class AConstant {

    //########################################################################
    // Public Cases

    public static final class BooleanValue extends AConstant {
        public final boolean value;

        private BooleanValue(boolean value) {
            this.value = value;
        }
    }

    public static final class ByteValue extends AConstant {
        public final byte value;

        private ByteValue(byte value) {
            this.value = value;
        }
    }

    public static final class ShortValue extends AConstant {
        public final short value;

        private ShortValue(short value) {
            this.value = value;
        }
    }

    public static final class CharValue extends AConstant {
        public final char value;

        private CharValue(char value) {
            this.value = value;
        }
    }

    public static final class IntValue extends AConstant {
        public final int value;

        private IntValue(int value) {
            this.value = value;
        }
    }

    public static final class LongValue extends AConstant {
        public final long value;

        private LongValue(long value) {
            this.value = value;
        }
    }

    public static final class FloatValue extends AConstant {
        public final float value;

        private FloatValue(float value) {
            this.value = value;
        }
    }

    public static final class DoubleValue extends AConstant {
        public final double value;

        private DoubleValue(double value) {
            this.value = value;
        }
    }

    public static final class StringValue extends AConstant {
        public final String value;

        private StringValue(String value) {
            this.value = value;
        }
    }

    private static final class UnitValue extends AConstant {
    }

    private static final class NullValue extends AConstant {
    }

    private static final class ZeroValue extends AConstant {
    }

    public static final AConstant UNIT = new UnitValue();
    public static final AConstant NULL = new NullValue();
    public static final AConstant ZERO = new ZeroValue();

    public static AConstant BOOLEAN(boolean value) {
        return new BooleanValue(value);
    }

    public static AConstant BYTE(byte value) {
        return new ByteValue(value);
    }

    public static AConstant SHORT(short value) {
        return new ShortValue(value);
    }

    public static AConstant CHAR(char value) {
        return new CharValue(value);
    }

    public static AConstant INT(int value) {
        return new IntValue(value);
    }

    public static AConstant LONG(long value) {
        return new LongValue(value);
    }

    public static AConstant FLOAT(float value) {
        return new FloatValue(value);
    }

    public static AConstant DOUBLE(double value) {
        return new DoubleValue(value);
    }

    public static AConstant STRING(String value) {
        return new StringValue(value);
    }

    //########################################################################
    // Public Methods

    /** Returns a string representation of this constant. */
    public String toString() {
        return new ATreePrinter().printConstant(this).toString();
    }

    //########################################################################
}
