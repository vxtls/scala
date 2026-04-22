/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a type kind. */
public class ATypeKind {

    //########################################################################
    // Public Cases

    private final String name;

    private ATypeKind(String name) {
        this.name = name;
    }

    /** A boolean value */
    public static final ATypeKind BOOL = new ATypeKind("BOOL");

    /** A 1-byte unsigned integer */
    public static final ATypeKind U1 = new ATypeKind("U1");

    /** A 2-byte unsigned integer */
    public static final ATypeKind U2 = new ATypeKind("U2");

    /** A 4-byte unsigned integer */
    public static final ATypeKind U4 = new ATypeKind("U4");

    /** An 8-byte unsigned integer */
    public static final ATypeKind U8 = new ATypeKind("U8");

    /** A 1-byte signed integer */
    public static final ATypeKind I1 = new ATypeKind("I1");

    /** A 2-byte signed integer */
    public static final ATypeKind I2 = new ATypeKind("I2");

    /** A 4-byte signed integer */
    public static final ATypeKind I4 = new ATypeKind("I4");

    /** An 8-byte signed integer */
    public static final ATypeKind I8 = new ATypeKind("I8");

    /** A 4-byte floating point number */
    public static final ATypeKind R4 = new ATypeKind("R4");

    /** An 8-byte floating point number */
    public static final ATypeKind R8 = new ATypeKind("R8");

    /** An object reference */
    public static final ATypeKind REF = new ATypeKind("REF");

    /** A string reference */
    public static final ATypeKind STR = new ATypeKind("STR");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this type kind. */
    public String toString() {
        return name;
    }

    //########################################################################
}
