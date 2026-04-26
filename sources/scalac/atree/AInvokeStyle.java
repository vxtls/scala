/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.util.Debug;

/** This class represents a method invocation style. */
public class AInvokeStyle {

    //########################################################################
    // Public Cases

    private final String name;
    private final Boolean onInstance;

    private AInvokeStyle(String name, Boolean onInstance) {
        this.name = name;
        this.onInstance = onInstance;
    }

    public static final AInvokeStyle New = new AInvokeStyle("new", null);
    public static final AInvokeStyle Dynamic = new AInvokeStyle("dynamic", null);
    public static final AInvokeStyle StaticClass = new AInvokeStyle("static-class", Boolean.FALSE);
    public static final AInvokeStyle StaticInstance = new AInvokeStyle("static-instance", Boolean.TRUE);

    public static AInvokeStyle Static(boolean onInstance) {
        return onInstance ? StaticInstance : StaticClass;
    }

    //########################################################################
    // Public Methods

    /** Is this a new object creation? */
    public boolean isNew() {
        return this == New;
    }

    /** Is this a dynamic method call? */
    public boolean isDynamic() {
        return this == Dynamic;
    }

    /** Is this a static method call? */
    public boolean isStatic() {
        return onInstance != null;
    }

    /** Is this an instance method call? */
    public boolean hasInstance() {
        if (this == Dynamic) return true;
        if (onInstance != null) return onInstance.booleanValue();
        return false;
    }

    /** Returns a string representation of this style. */
    public String toString() {
        if (this == New || this == Dynamic || this == StaticClass || this == StaticInstance) {
            return name;
        }
        throw Debug.abort("unknown case", this);
    }

    //########################################################################
}
