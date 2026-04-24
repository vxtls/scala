/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.Global;
import scalac.util.Name;

final class AbsTypeSymbol extends TypeSymbol {

    private Type lobound = null;

    AbsTypeSymbol(int pos, Name name, Symbol owner, int flags, int attrs) {
        super(TYPE, pos, name, owner, flags, attrs);
        allConstructors().setInfo(Type.MethodType(EMPTY_ARRAY, Type.typeRef(owner.thisType(), this, Type.EMPTY_ARRAY)));
    }

    public Type loBound() {
        initialize();
        return lobound == null ? Global.instance.definitions.ALL_TYPE() : lobound;
    }

    public Symbol setLoBound(Type lobound) {
        this.lobound = lobound;
        return this;
    }

    protected TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs) {
        TypeSymbol clone = new AbsTypeSymbol(pos, name, owner, flags, attrs);
        clone.setLoBound(loBound());
        return clone;
    }
}
