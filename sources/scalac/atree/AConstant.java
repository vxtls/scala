/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.symtab.Symbol;
import scalac.util.Debug;

/** This class represents a constant. */
public class AConstant {

    public static final AConstant[] EMPTY_ARRAY = new AConstant[0];

    //########################################################################
    // Public Cases

    public static class UNIT extends AConstant {
        public UNIT() {
        }
    }

    public static class BOOLEAN extends AConstant {
        public final boolean value;

        public BOOLEAN(boolean value) {
            this.value = value;
        }
    }

    public static class BYTE extends AConstant {
        public final byte value;

        public BYTE(byte value) {
            this.value = value;
        }
    }

    public static class SHORT extends AConstant {
        public final short value;

        public SHORT(short value) {
            this.value = value;
        }
    }

    public static class CHAR extends AConstant {
        public final char value;

        public CHAR(char value) {
            this.value = value;
        }
    }

    public static class INT extends AConstant {
        public final int value;

        public INT(int value) {
            this.value = value;
        }
    }

    public static class LONG extends AConstant {
        public final long value;

        public LONG(long value) {
            this.value = value;
        }
    }

    public static class FLOAT extends AConstant {
        public final float value;

        public FLOAT(float value) {
            this.value = value;
        }
    }

    public static class DOUBLE extends AConstant {
        public final double value;

        public DOUBLE(double value) {
            this.value = value;
        }
    }

    public static class STRING extends AConstant {
        public final String value;

        public STRING(String value) {
            this.value = value;
        }
    }

    public static class SYMBOL_NAME extends AConstant {
        public final Symbol value;

        public SYMBOL_NAME(Symbol value) {
            this.value = value;
        }
    }

    public static class NULL extends AConstant {
        public NULL() {
        }
    }

    public static class ZERO extends AConstant {
        public ZERO() {
        }
    }

    public static final class BooleanValue extends BOOLEAN {
        public BooleanValue(boolean value) {
            super(value);
        }
    }

    public static final class ByteValue extends BYTE {
        public ByteValue(byte value) {
            super(value);
        }
    }

    public static final class ShortValue extends SHORT {
        public ShortValue(short value) {
            super(value);
        }
    }

    public static final class CharValue extends CHAR {
        public CharValue(char value) {
            super(value);
        }
    }

    public static final class IntValue extends INT {
        public IntValue(int value) {
            super(value);
        }
    }

    public static final class LongValue extends LONG {
        public LongValue(long value) {
            super(value);
        }
    }

    public static final class FloatValue extends FLOAT {
        public FloatValue(float value) {
            super(value);
        }
    }

    public static final class DoubleValue extends DOUBLE {
        public DoubleValue(double value) {
            super(value);
        }
    }

    public static final class StringValue extends STRING {
        public StringValue(String value) {
            super(value);
        }
    }

    public static final class SymbolNameValue extends SYMBOL_NAME {
        public SymbolNameValue(Symbol value) {
            super(value);
        }
    }

    private static final class UnitValue extends UNIT {
        private UnitValue() {
        }
    }

    private static final class NullValue extends NULL {
        private NullValue() {
        }
    }

    private static final class ZeroValue extends ZERO {
        private ZeroValue() {
        }
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

    public static AConstant SYMBOL_NAME(Symbol value) {
        return new SymbolNameValue(value);
    }

    //########################################################################
    // Public Methods

    /** Returns the type kind of this constant. */
    public ATypeKind kind() {
        if (this == UNIT) return ATypeKind.UNIT;
        if (this instanceof BOOLEAN) return ATypeKind.BOOL;
        if (this instanceof BYTE) return ATypeKind.I1;
        if (this instanceof SHORT) return ATypeKind.I2;
        if (this instanceof CHAR) return ATypeKind.U2;
        if (this instanceof INT) return ATypeKind.I4;
        if (this instanceof LONG) return ATypeKind.I8;
        if (this instanceof FLOAT) return ATypeKind.R4;
        if (this instanceof DOUBLE) return ATypeKind.R8;
        if (this instanceof STRING) return ATypeKind.STR;
        if (this instanceof SYMBOL_NAME) return ATypeKind.STR;
        if (this == NULL) return ATypeKind.NULL;
        if (this == ZERO) return ATypeKind.ZERO;
        throw Debug.abort("unknown case", this);
    }

    /** Converts this constant to a boolean value. */
    public boolean booleanValue() {
        if (this instanceof BOOLEAN) return ((BOOLEAN) this).value;
        throw Debug.abort("not convertible to boolean", this);
    }

    /** Converts this constant to a byte value. */
    public byte byteValue() {
        if (this instanceof BYTE) return ((BYTE) this).value;
        if (this instanceof SHORT) return (byte) ((SHORT) this).value;
        if (this instanceof CHAR) return (byte) ((CHAR) this).value;
        if (this instanceof INT) return (byte) ((INT) this).value;
        if (this instanceof LONG) return (byte) ((LONG) this).value;
        if (this instanceof FLOAT) return (byte) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (byte) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to byte", this);
    }

    /** Converts this constant to a short value. */
    public short shortValue() {
        if (this instanceof BYTE) return (short) ((BYTE) this).value;
        if (this instanceof SHORT) return ((SHORT) this).value;
        if (this instanceof CHAR) return (short) ((CHAR) this).value;
        if (this instanceof INT) return (short) ((INT) this).value;
        if (this instanceof LONG) return (short) ((LONG) this).value;
        if (this instanceof FLOAT) return (short) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (short) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to short", this);
    }

    /** Converts this constant to a char value. */
    public char charValue() {
        if (this instanceof BYTE) return (char) ((BYTE) this).value;
        if (this instanceof SHORT) return (char) ((SHORT) this).value;
        if (this instanceof CHAR) return ((CHAR) this).value;
        if (this instanceof INT) return (char) ((INT) this).value;
        if (this instanceof LONG) return (char) ((LONG) this).value;
        if (this instanceof FLOAT) return (char) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (char) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to char", this);
    }

    /** Converts this constant to a int value. */
    public int intValue() {
        if (this instanceof BYTE) return (int) ((BYTE) this).value;
        if (this instanceof SHORT) return (int) ((SHORT) this).value;
        if (this instanceof CHAR) return (int) ((CHAR) this).value;
        if (this instanceof INT) return ((INT) this).value;
        if (this instanceof LONG) return (int) ((LONG) this).value;
        if (this instanceof FLOAT) return (int) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (int) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to int", this);
    }

    /** Converts this constant to a long value. */
    public long longValue() {
        if (this instanceof BYTE) return (long) ((BYTE) this).value;
        if (this instanceof SHORT) return (long) ((SHORT) this).value;
        if (this instanceof CHAR) return (long) ((CHAR) this).value;
        if (this instanceof INT) return (long) ((INT) this).value;
        if (this instanceof LONG) return ((LONG) this).value;
        if (this instanceof FLOAT) return (long) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (long) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to long", this);
    }

    /** Converts this constant to a float value. */
    public float floatValue() {
        if (this instanceof BYTE) return (float) ((BYTE) this).value;
        if (this instanceof SHORT) return (float) ((SHORT) this).value;
        if (this instanceof CHAR) return (float) ((CHAR) this).value;
        if (this instanceof INT) return (float) ((INT) this).value;
        if (this instanceof LONG) return (float) ((LONG) this).value;
        if (this instanceof FLOAT) return ((FLOAT) this).value;
        if (this instanceof DOUBLE) return (float) ((DOUBLE) this).value;
        throw Debug.abort("not convertible to float", this);
    }

    /** Converts this constant to a double value. */
    public double doubleValue() {
        if (this instanceof BYTE) return (double) ((BYTE) this).value;
        if (this instanceof SHORT) return (double) ((SHORT) this).value;
        if (this instanceof CHAR) return (double) ((CHAR) this).value;
        if (this instanceof INT) return (double) ((INT) this).value;
        if (this instanceof LONG) return (double) ((LONG) this).value;
        if (this instanceof FLOAT) return (double) ((FLOAT) this).value;
        if (this instanceof DOUBLE) return ((DOUBLE) this).value;
        throw Debug.abort("not convertible to double", this);
    }

    /** Converts this constant to a String value. */
    public String stringValue() {
        if (this == UNIT) return "()";
        if (this instanceof BOOLEAN) return String.valueOf(((BOOLEAN) this).value);
        if (this instanceof BYTE) return String.valueOf(((BYTE) this).value);
        if (this instanceof SHORT) return String.valueOf(((SHORT) this).value);
        if (this instanceof CHAR) return String.valueOf(((CHAR) this).value);
        if (this instanceof INT) return String.valueOf(((INT) this).value);
        if (this instanceof LONG) return String.valueOf(((LONG) this).value);
        if (this instanceof FLOAT) return String.valueOf(((FLOAT) this).value);
        if (this instanceof DOUBLE) return String.valueOf(((DOUBLE) this).value);
        if (this instanceof STRING) return ((STRING) this).value;
        if (this instanceof SYMBOL_NAME) return ((SYMBOL_NAME) this).value.name.toString();
        if (this == NULL) return String.valueOf(null);
        throw Debug.abort("not convertible to String", this);
    }

    /** Returns a string representation of this constant. */
    public String toString() {
        return new ATreePrinter().printConstant(this).toString();
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        if (this instanceof BOOLEAN) return ((BOOLEAN)this).value == ((BOOLEAN)other).value;
        if (this instanceof BYTE) return ((BYTE)this).value == ((BYTE)other).value;
        if (this instanceof SHORT) return ((SHORT)this).value == ((SHORT)other).value;
        if (this instanceof CHAR) return ((CHAR)this).value == ((CHAR)other).value;
        if (this instanceof INT) return ((INT)this).value == ((INT)other).value;
        if (this instanceof LONG) return ((LONG)this).value == ((LONG)other).value;
        if (this instanceof FLOAT)
            return Float.floatToIntBits(((FLOAT)this).value) == Float.floatToIntBits(((FLOAT)other).value);
        if (this instanceof DOUBLE)
            return Double.doubleToLongBits(((DOUBLE)this).value) == Double.doubleToLongBits(((DOUBLE)other).value);
        if (this instanceof STRING) return ((STRING)this).value.equals(((STRING)other).value);
        if (this instanceof SYMBOL_NAME) return ((SYMBOL_NAME)this).value == ((SYMBOL_NAME)other).value;
        return true;
    }

    public int hashCode() {
        int result = getClass().hashCode();
        if (this instanceof BOOLEAN) return 31 * result + (((BOOLEAN)this).value ? 1 : 0);
        if (this instanceof BYTE) return 31 * result + ((BYTE)this).value;
        if (this instanceof SHORT) return 31 * result + ((SHORT)this).value;
        if (this instanceof CHAR) return 31 * result + ((CHAR)this).value;
        if (this instanceof INT) return 31 * result + ((INT)this).value;
        if (this instanceof LONG) {
            long value = ((LONG)this).value;
            return 31 * result + (int)(value ^ (value >>> 32));
        }
        if (this instanceof FLOAT) return 31 * result + Float.floatToIntBits(((FLOAT)this).value);
        if (this instanceof DOUBLE) {
            long value = Double.doubleToLongBits(((DOUBLE)this).value);
            return 31 * result + (int)(value ^ (value >>> 32));
        }
        if (this instanceof STRING) return 31 * result + ((STRING)this).value.hashCode();
        if (this instanceof SYMBOL_NAME) return 31 * result + ((SYMBOL_NAME)this).value.hashCode();
        return result;
    }

    //########################################################################
}
