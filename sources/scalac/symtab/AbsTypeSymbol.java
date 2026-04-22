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

public class AbsTypeSymbol extends TypeSymbol {

    private Type lobound = null;

    public AbsTypeSymbol(int pos, Name name, Symbol owner, int flags) {
        super(TYPE, pos, name, owner, flags);
        allConstructors().setFirstInfo(Type.MethodType(EMPTY_ARRAY, Type.TypeRef(owner.thisType(), this, Type.EMPTY_ARRAY)));
    }

    public static AbsTypeSymbol define(
        int pos, Name name, Symbol owner, int flags, Scope scope) {
        Scope.Entry e = scope.lookupEntry(name);
        if (e.owner == scope && e.sym.isExternal() && e.sym.kind == TYPE) {
            AbsTypeSymbol sym = (AbsTypeSymbol) e.sym;
            sym.update(pos, flags);
            return sym;
        } else {
            return new AbsTypeSymbol(pos, name, owner, flags);
        }
    }

    public Symbol cloneSymbol(Symbol owner) {
        TypeSymbol other = new AbsTypeSymbol(pos, name, owner, flags);
        other.setInfo(info());
        other.setLoBound(loBound());
        return other;
    }

    public void copyTo(Symbol sym) {
        super.copyTo(sym);
        ((AbsTypeSymbol) sym).lobound = lobound;
    }

    public Type loBound() {
        initialize();
        return lobound == null ? Global.instance.definitions.ALL_TYPE() : lobound;
    }

    public Symbol setLoBound(Type lobound) {
        this.lobound = lobound;
        return this;
    }
}
