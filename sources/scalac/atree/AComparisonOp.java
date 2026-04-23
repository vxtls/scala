/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a comparison operation. */
public class AComparisonOp {

    //########################################################################
    // Public Cases

    private final String name;

    private AComparisonOp(String name) {
        this.name = name;
    }

    /** A comparison operation with -1 default for NaNs */
    public static final AComparisonOp CMPL = new AComparisonOp("CMPL");

    /** A comparison operation with no default for NaNs */
    public static final AComparisonOp CMP = new AComparisonOp("CMP");

    /** A comparison operation with +1 default for NaNs */
    public static final AComparisonOp CMPG = new AComparisonOp("CMPG");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this operation. */
    public String toString() {
        return name;
    }

    //########################################################################
}
