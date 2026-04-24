/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.symtab.Type;
import scalac.symtab.Symbol;

/** This class represents an attributed function reference. */
public class AFunction {

    //########################################################################
    // Public Cases

    public static final class Method extends AFunction {
        public final ACode object;
        public final Symbol method;
        public final AInvokeStyle style;

        public Method(ACode object, Symbol method, AInvokeStyle style) {
            this.object = object;
            this.method = method;
            this.style = style;
        }
    }

    public static final class Primitive extends AFunction {
        public final APrimitive primitive;

        public Primitive(APrimitive primitive) {
            this.primitive = primitive;
        }
    }

    public static final class NewArray extends AFunction {
        public final Type element;

        public NewArray(Type element) {
            this.element = element;
        }
    }

    public static Method Method(ACode object, Symbol method, AInvokeStyle style) {
        return new Method(object, method, style);
    }

    public static Primitive Primitive(APrimitive primitive) {
        return new Primitive(primitive);
    }

    public static NewArray NewArray(Type element) {
        return new NewArray(element);
    }

    //########################################################################
    // Public Methods

    /** Returns a string representation of this function. */
    public String toString() {
        return new ATreePrinter().printFunction(this).toString();
    }

    //########################################################################
}
