/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

import meta.java.JavaWriter;

/**
 * This class describes the possible links between a given field of a
 * tree and the symbol of that tree.
 */
public final class TreeFieldLink {

    //########################################################################
    // Public Cases

    /** Field is linked to the symbol's flags */
    public static final TreeFieldLink SymFlags = new TreeFieldLink("flags");

    /** Field is linked to the symbol's name */
    public static final TreeFieldLink SymName = new TreeFieldLink("name");

    private final String link;

    private TreeFieldLink(String link) {
        this.link = link;
    }

    //########################################################################
    // Public Methods

    /** Returns the field or method to invoke to get the linked value. */
    public String getLink() {
        return link;
    }

    /** Returns the name of this link. */
    public String toString() {
        return link;
    }

    public JavaWriter print(JavaWriter writer, TreeField symbol) {
        return writer.print(symbol.name).print('.').print(getLink());
    }

    //########################################################################
}
