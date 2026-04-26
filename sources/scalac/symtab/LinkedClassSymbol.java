/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scala.tools.util.Position;

import scalac.util.Name;

final class LinkedClassSymbol extends ClassSymbol {

    private final LinkedModuleSymbol module;

    LinkedClassSymbol(Symbol owner, int flags, Name name) {
        super(owner, Position.NOPOS, flags, name, 0);
        this.module = new LinkedModuleSymbol(this);
    }

    public ModuleSymbol linkedModule() {
        return module;
    }
}
