/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// Old$Id$
// $Id$

package scalac.backend;

/**
 * Primitive functions.
 *
 * @author Michel Schinz, Philippe Altherr
 * @version 1.0
 */

public enum Primitive {

    // Non-primitive operations
    NOT_A_PRIMITIVE,              // not a primitive

    // Arithmetic unary operations
    POS,                          // +x
    NEG,                          // -x
    NOT,                          // ~x

    // Arithmetic binary operations
    ADD,                          // x + y
    SUB,                          // x - y
    MUL,                          // x * y
    DIV,                          // x / y
    MOD,                          // x % y

    // Bitwise operations
    OR,                           // x | y
    XOR,                          // x ^ y
    AND,                          // x & y

    // Shift operations
    LSL,                          // x << y
    LSR,                          // x >>> y
    ASR,                          // x >> y

    // Comparison operations
    ID,                           // x eq y
    NI,                           // x ne y
    EQ,                           // x == y
    NE,                           // x != y
    LT,                           // x < y
    LE,                           // x <= y
    GE,                           // x > y
    GT,                           // x >= y

    // Boolean unary operations
    ZNOT,                         // !x

    // Boolean binary operations
    ZOR,                          // x || y
    ZAND,                         // x && y

    // Array operations
    LENGTH,                       // x.length
    APPLY,                        // x(y)
    UPDATE,                       // x(y) = z

    // Any operations
    IS,                           // x.is[y]
    AS,                           // x.as[y]
    EQUALS,                       // x.equals(y)
    HASHCODE,                     // x.hashcode()
    TOSTRING,                     // x.toString()

    // AnyRef operations
    SYNCHRONIZED,                 // x.synchronized(y)

    // String operations
    CONCAT,                       // String.valueOf(x)+String.valueOf(y)

    // Throwable operations
    THROW,                        // throw x

    // Value types conversions
    COERCE,                       // x.coerce()

    // RunTime operations
    BOX,                          // RunTime.box_<X>(x)
    UNBOX,                        // RunTime.unbox_<X>(x)
    NEW_ZARRAY,                   // RunTime.zarray(x)
    NEW_BARRAY,                   // RunTime.barray(x)
    NEW_SARRAY,                   // RunTime.sarray(x)
    NEW_CARRAY,                   // RunTime.carray(x)
    NEW_IARRAY,                   // RunTime.iarray(x)
    NEW_LARRAY,                   // RunTime.larray(x)
    NEW_FARRAY,                   // RunTime.farray(x)
    NEW_DARRAY,                   // RunTime.darray(x)
    NEW_OARRAY,                   // RunTime.oarray(x)
    ZARRAY_LENGTH,                // RunTime.zarray_length(x)
    BARRAY_LENGTH,                // RunTime.barray_length(x)
    SARRAY_LENGTH,                // RunTime.sarray_length(x)
    CARRAY_LENGTH,                // RunTime.carray_length(x)
    IARRAY_LENGTH,                // RunTime.iarray_length(x)
    LARRAY_LENGTH,                // RunTime.larray_length(x)
    FARRAY_LENGTH,                // RunTime.farray_length(x)
    DARRAY_LENGTH,                // RunTime.darray_length(x)
    OARRAY_LENGTH,                // RunTime.oarray_length(x)
    ZARRAY_GET,                   // RunTime.zarray_get(x,y)
    BARRAY_GET,                   // RunTime.barray_get(x,y)
    SARRAY_GET,                   // RunTime.sarray_get(x,y)
    CARRAY_GET,                   // RunTime.carray_get(x,y)
    IARRAY_GET,                   // RunTime.iarray_get(x,y)
    LARRAY_GET,                   // RunTime.larray_get(x,y)
    FARRAY_GET,                   // RunTime.farray_get(x,y)
    DARRAY_GET,                   // RunTime.darray_get(x,y)
    OARRAY_GET,                   // RunTime.oarray_get(x,y)
    ZARRAY_SET,                   // RunTime.zarray(x,y,z)
    BARRAY_SET,                   // RunTime.barray(x,y,z)
    SARRAY_SET,                   // RunTime.sarray(x,y,z)
    CARRAY_SET,                   // RunTime.carray(x,y,z)
    IARRAY_SET,                   // RunTime.iarray(x,y,z)
    LARRAY_SET,                   // RunTime.larray(x,y,z)
    FARRAY_SET,                   // RunTime.farray(x,y,z)
    DARRAY_SET,                   // RunTime.darray(x,y,z)
    OARRAY_SET,                   // RunTime.oarray(x,y,z)

    B2B,                          // RunTime.b2b(x)
    B2S,                          // RunTime.b2s(x)
    B2C,                          // RunTime.b2c(x)
    B2I,                          // RunTime.b2i(x)
    B2L,                          // RunTime.b2l(x)
    B2F,                          // RunTime.b2f(x)
    B2D,                          // RunTime.b2d(x)
    S2B,                          // RunTime.s2b(x)
    S2S,                          // RunTime.s2s(x)
    S2C,                          // RunTime.s2c(x)
    S2I,                          // RunTime.s2i(x)
    S2L,                          // RunTime.s2l(x)
    S2F,                          // RunTime.s2f(x)
    S2D,                          // RunTime.s2d(x)
    C2B,                          // RunTime.c2b(x)
    C2S,                          // RunTime.c2s(x)
    C2C,                          // RunTime.c2c(x)
    C2I,                          // RunTime.c2i(x)
    C2L,                          // RunTime.c2l(x)
    C2F,                          // RunTime.c2f(x)
    C2D,                          // RunTime.c2d(x)
    I2B,                          // RunTime.i2b(x)
    I2S,                          // RunTime.i2s(x)
    I2C,                          // RunTime.i2c(x)
    I2I,                          // RunTime.i2i(x)
    I2L,                          // RunTime.i2l(x)
    I2F,                          // RunTime.i2f(x)
    I2D,                          // RunTime.i2d(x)
    L2B,                          // RunTime.l2b(x)
    L2S,                          // RunTime.l2s(x)
    L2C,                          // RunTime.l2c(x)
    L2I,                          // RunTime.l2i(x)
    L2L,                          // RunTime.l2l(x)
    L2F,                          // RunTime.l2f(x)
    L2D,                          // RunTime.l2d(x)
    F2B,                          // RunTime.f2b(x)
    F2S,                          // RunTime.f2s(x)
    F2C,                          // RunTime.f2c(x)
    F2I,                          // RunTime.f2i(x)
    F2L,                          // RunTime.f2l(x)
    F2F,                          // RunTime.f2f(x)
    F2D,                          // RunTime.f2d(x)
    D2B,                          // RunTime.d2b(x)
    D2S,                          // RunTime.d2s(x)
    D2C,                          // RunTime.d2c(x)
    D2I,                          // RunTime.d2i(x)
    D2L,                          // RunTime.d2l(x)
    D2F,                          // RunTime.d2f(x)
    D2D;                          // RunTime.d2d(x)

    /** Return negated version of comparison primitive. */
    public Primitive negate() {
        switch (this) {
        case LT: return Primitive.GE;
        case LE: return Primitive.GT;
        case EQ: return Primitive.NE;
        case NE: return Primitive.EQ;
        case GE: return Primitive.LT;
        case GT: return Primitive.LE;
        default: throw new IllegalStateException("unknown primitive " + this);
        }
    }

    /** Return primitive with arguments swapped (e.g. <= is turned
     ** into =>). */
    public Primitive swap() {
        switch (this) {
        case LT: return Primitive.GT;
        case LE: return Primitive.GE;
        case EQ: return Primitive.EQ;
        case NE: return Primitive.NE;
        case GE: return Primitive.LE;
        case GT: return Primitive.LT;
        default: throw new IllegalStateException("unknown primitive " + this);
        }
    }
}
