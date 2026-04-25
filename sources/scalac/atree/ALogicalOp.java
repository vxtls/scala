/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a logical operation. */
public class ALogicalOp {

    //########################################################################
    // Public Cases

    private final String name;

    private ALogicalOp(String name) {
        this.name = name;
    }

    /** A bitwise AND operation */
    public static final ALogicalOp AND = new ALogicalOp("AND");

    /** A bitwise OR operation */
    public static final ALogicalOp OR = new ALogicalOp("OR");

    /** A bitwise XOR operation */
    public static final ALogicalOp XOR = new ALogicalOp("XOR");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this operation. */
    public String toString() {
        return name;
    }

    //########################################################################
}
