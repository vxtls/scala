/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.util.Debug;
import scalac.util.Name;

public class ModuleSymbol extends TermSymbol {

    private final ModuleClassSymbol clasz;

    private ModuleSymbol(Symbol owner, int pos, int flags, Name name,
        int attrs, ModuleClassSymbol clasz)
    {
        super(owner, pos, flags | MODUL | FINAL | STABLE, name, attrs);
        this.clasz = clasz != null ? clasz : new ModuleClassSymbol(this);
        setType(Type.typeRef(owner().thisType(), this.clasz, Type.EMPTY_ARRAY));
    }

    ModuleSymbol(Symbol owner, int pos, int flags, Name name) {
        this(owner, pos, flags, name, 0, null);
    }

    public ModuleClassSymbol moduleClass() {
        assert isModule(): Debug.show(this);
        return clasz;
    }

    protected final Symbol cloneSymbolImpl(Symbol owner, int attrs) {
        return new ModuleSymbol(owner, pos, flags, name, attrs, clasz);
    }
}
