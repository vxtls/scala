/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a shift operation. */
public class AShiftOp {

    //########################################################################
    // Public Cases

    private final String name;

    private AShiftOp(String name) {
        this.name = name;
    }

    /** A logical shift to the left */
    public static final AShiftOp LSL = new AShiftOp("LSL");

    /** An arithmetic shift to the right */
    public static final AShiftOp ASR = new AShiftOp("ASR");

    /** A logical shift to the right */
    public static final AShiftOp LSR = new AShiftOp("LSR");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this operation. */
    public String toString() {
        return name;
    }

    //########################################################################
}
