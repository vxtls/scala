/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.util.Debug;

/** This class represents a test operation. */
public class ATestOp {

    //########################################################################
    // Public Cases

    private final String name;

    private ATestOp(String name) {
        this.name = name;
    }

    /** An equality test */
    public static final ATestOp EQ = new ATestOp("EQ");

    /** A non-equality test */
    public static final ATestOp NE = new ATestOp("NE");

    /** A less-than test */
    public static final ATestOp LT = new ATestOp("LT");

    /** A greater-than-or-equal test */
    public static final ATestOp GE = new ATestOp("GE");

    /** A less-than-or-equal test */
    public static final ATestOp LE = new ATestOp("LE");

    /** A greater-than test */
    public static final ATestOp GT = new ATestOp("GT");

    //########################################################################
    // Public Methods

    /** Returns the negation of this operation. */
    public ATestOp negate() {
        if (this == EQ) return NE;
        if (this == NE) return EQ;
        if (this == LT) return GE;
        if (this == GE) return LT;
        if (this == LE) return GT;
        if (this == GT) return LE;
        throw Debug.abort("unknown case", this);
    }

    /** Returns a string representation of this operation. */
    public String toString() {
        return name;
    }

    //########################################################################
}
