/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

final class LinkedModuleSymbol extends ModuleSymbol {

    private final LinkedClassSymbol clasz;

    LinkedModuleSymbol(LinkedClassSymbol clasz) {
        super(clasz.owner(), clasz.pos, clasz.flags & JAVA,
            clasz.name.toTermName());
        this.clasz = clasz;
    }

    public ClassSymbol linkedClass() {
        return clasz;
    }
}
