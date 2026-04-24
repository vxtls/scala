/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.util.Name;

final class AliasTypeSymbol extends TypeSymbol {

    AliasTypeSymbol(int pos, Name name, Symbol owner, int flags, int attrs) {
        super(ALIAS, pos, name, owner, flags, attrs);
    }

    protected TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs) {
        return new AliasTypeSymbol(pos, name, owner, flags, attrs);
    }
}
