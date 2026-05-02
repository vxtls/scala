/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

/** This class describes the symbol stored in some tree nodes. */
public abstract class TreeSymbol {

    //########################################################################
    // Public Constants

    /** Indicates the absence of symbol. */
    public static final TreeSymbol NoSym = new NoSymValue();

    //########################################################################
    // Public Constructors

    protected TreeSymbol() {
    }

    //########################################################################
    // Public Factories

    /** Indicates the presence of a symbol. */
    public static HasSym HasSym(TreeField field, boolean isDef) {
        return new HasSym(field, isDef);
    }

    //########################################################################
    // Public Classes

    private static final class NoSymValue extends TreeSymbol {
        private NoSymValue() {
        }
    }

    /** Indicates the presence of a symbol. */
    public static final class HasSym extends TreeSymbol {
        public final TreeField field;
        public final boolean isDef;

        private HasSym(TreeField field, boolean isDef) {
            this.field = field;
            this.isDef = isDef;
        }
    }

    //########################################################################
}
