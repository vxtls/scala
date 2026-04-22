/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

/** This class represents a method invocation style. */
public class AInvokeStyle {

    //########################################################################
    // Public Cases

    private final String name;

    private AInvokeStyle(String name) {
        this.name = name;
    }

    public static final AInvokeStyle New = new AInvokeStyle("new");
    public static final AInvokeStyle Static = new AInvokeStyle("static");
    public static final AInvokeStyle Dynamic = new AInvokeStyle("dynamic");

    //########################################################################
    // Public Methods

    /** Returns a string representation of this style. */
    public String toString() {
        return name;
    }

    //########################################################################
}
