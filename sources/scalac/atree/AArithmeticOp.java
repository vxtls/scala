/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents an arithmetic operation. */
public class AArithmeticOp {

    //########################################################################
    // Public Cases

    private final String name;

    private AArithmeticOp(String name) {
        this.name = name;
    }

    /** An arithmetic addition operation */
    public static final AArithmeticOp ADD = new AArithmeticOp("ADD");

    /** An arithmetic subtraction operation */
    public static final AArithmeticOp SUB = new AArithmeticOp("SUB");

    /** An arithmetic multiplication operation */
    public static final AArithmeticOp MUL = new AArithmeticOp("MUL");

    /** An arithmetic division operation */
    public static final AArithmeticOp DIV = new AArithmeticOp("DIV");

    /** An arithmetic remainder operation */
    public static final AArithmeticOp REM = new AArithmeticOp("REM");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this operation. */
    public String toString() {
        return name;
    }

    //########################################################################
}
