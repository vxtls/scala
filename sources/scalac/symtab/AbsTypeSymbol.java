/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scalac.Global;
import scalac.util.Name;

final class AbsTypeSymbol extends TypeSymbol {

    private Type lobound = null;
    private Type vubound = null;

    AbsTypeSymbol(Symbol owner, int pos, int flags, Name name, int attrs) {
        super(TYPE, owner, pos, flags, name, attrs);
        allConstructors().setInfo(Type.MethodType(EMPTY_ARRAY, Type.typeRef(owner.thisType(), this, Type.EMPTY_ARRAY)));
    }

    public Type loBound() {
        initialize();
        return lobound == null ? Global.instance.definitions.ALL_TYPE() : lobound;
    }

    public Type vuBound() {
        initialize();
        return !isViewBounded() || vubound == null
            ? Global.instance.definitions.ANY_TYPE() : vubound;
    }

    public Symbol setLoBound(Type lobound) {
        this.lobound = lobound;
        return this;
    }

    public Symbol setVuBound(Type vubound) {
        this.vubound = vubound;
        return this;
    }

    protected TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs) {
        TypeSymbol clone = new AbsTypeSymbol(owner, pos, flags, name, attrs);
        clone.setLoBound(loBound());
        clone.setVuBound(vuBound());
        return clone;
    }
}
