/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.atree;

import scalac.symtab.Symbol;

/** This class represents an attributed value location. */
public abstract class ALocation {

    //########################################################################
    // Public Cases

    public static final class Module extends ALocation {
        public final Symbol module;

        private Module(Symbol module) {
            this.module = module;
        }
    }

    public static final class Field extends ALocation {
        public final ACode object;
        public final Symbol field;
        public final boolean isStatic;

        private Field(ACode object, Symbol field, boolean isStatic) {
            this.object = object;
            this.field = field;
            this.isStatic = isStatic;
        }
    }

    public static final class Local extends ALocation {
        public final Symbol local;
        public final boolean isArgument;

        private Local(Symbol local, boolean isArgument) {
            this.local = local;
            this.isArgument = isArgument;
        }
    }

    public static final class ArrayItem extends ALocation {
        public final ACode array;
        public final ACode index;

        private ArrayItem(ACode array, ACode index) {
            this.array = array;
            this.index = index;
        }
    }

    public static Module Module(Symbol module) {
        return new Module(module);
    }

    public static Field Field(ACode object, Symbol field, boolean isStatic) {
        return new Field(object, field, isStatic);
    }

    public static Local Local(Symbol local, boolean isArgument) {
        return new Local(local, isArgument);
    }

    public static ArrayItem ArrayItem(ACode array, ACode index) {
        return new ArrayItem(array, index);
    }

    //########################################################################
    // Public Methods

    /** Returns a string representation of this location. */
    public String toString() {
        return new ATreePrinter().printLocation(this).toString();
    }

    //########################################################################
}
