/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

public class CyclicReference extends Type.Error {
    public Symbol sym;
    public Type info;

    public CyclicReference(Symbol sym, Type info) {
        super("illegal cyclic reference involving " + sym);
        this.sym = sym;
        this.info = info;
    }
}
