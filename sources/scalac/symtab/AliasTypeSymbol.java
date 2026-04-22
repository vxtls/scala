/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.util.Name;

public class AliasTypeSymbol extends TypeSymbol {

    public AliasTypeSymbol(int pos, Name name, Symbol owner, int flags) {
        super(ALIAS, pos, name, owner, flags);
    }

    public static AliasTypeSymbol define(
        int pos, Name name, Symbol owner, int flags, Scope scope) {
        Scope.Entry e = scope.lookupEntry(name);
        if (e.owner == scope && e.sym.isExternal() && e.sym.kind == ALIAS) {
            AliasTypeSymbol sym = (AliasTypeSymbol) e.sym;
            sym.update(pos, flags);
            return sym;
        } else {
            return new AliasTypeSymbol(pos, name, owner, flags);
        }
    }

    public Symbol cloneSymbol(Symbol owner) {
        AliasTypeSymbol other = new AliasTypeSymbol(pos, name, owner, flags);
        other.setInfo(info());
        copyConstructorInfo(other);
        return other;
    }
}
