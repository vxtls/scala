/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scalac.util.Name;

final class AliasTypeSymbol extends TypeSymbol {

    AliasTypeSymbol(Symbol owner, int pos, int flags, Name name, int attrs) {
        super(ALIAS, owner, pos, flags, name, attrs);
    }

    protected TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs) {
        return new AliasTypeSymbol(owner, pos, flags, name, attrs);
    }
}
