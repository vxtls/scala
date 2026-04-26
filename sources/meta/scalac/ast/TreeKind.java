/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

/** This class enumerates the different kinds of tree nodes. */
public final class TreeKind {

    //########################################################################
    // Public Cases

    /** Designates a type, a term or anything else. */
    public static final TreeKind Any = new TreeKind("Any");

    /** Designates a type. */
    public static final TreeKind Type = new TreeKind("Type");

    /** Designates a term. */
    public static final TreeKind Term = new TreeKind("Term");

    /** Designates either a type or a term. */
    public static final TreeKind Dual = new TreeKind("Dual");

    /** Designates either a type or a term (a test may indicate which one). */
    public static final TreeKind Test = new TreeKind("Test");

    /** Designates neither a type nor a term. */
    public static final TreeKind None = new TreeKind("None");

    private final String name;

    private TreeKind(String name) {
        this.name = name;
    }

    //########################################################################
    // Public Method

    public boolean isA(TreeKind that) {
        if (this == Any) {
            return true;
        }
        if (this == Type) {
            return that == Type;
        }
        if (this == Term) {
            return that == Term;
        }
        if (this == Dual || this == Test) {
            return that == Type || that == Term || that == Dual;
        }
        if (this == None) {
            return that == None;
        }
        throw new Error();
    }

    public String toString() {
        return name;
    }

    //########################################################################
}
