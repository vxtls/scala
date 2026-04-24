/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.util.Debug;
import scalac.util.Names;

final class ConstructorSymbol extends TermSymbol {

    private final Symbol clasz;

    ConstructorSymbol(Symbol clasz, int pos, int flags) {
        super(clasz.owner(), pos, flags, Names.CONSTRUCTOR, 0);
        this.clasz = clasz;
    }

    public boolean isInitializer() {
        return false;
    }

    public boolean isConstructor() {
        return true;
    }

    public Symbol constructorClass() {
        return clasz;
    }

    protected final Symbol cloneSymbolImpl(Symbol owner, int attrs) {
        throw Debug.abort("illegal clone of constructor", this);
    }
}
