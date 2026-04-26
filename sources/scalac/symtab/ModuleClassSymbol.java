/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

public final class ModuleClassSymbol extends ClassSymbol {

    private final ModuleSymbol module;

    ModuleClassSymbol(ModuleSymbol module) {
        super(module.owner(), module.pos,
            (module.flags & MODULE2CLASSFLAGS) | MODUL | FINAL,
            module.name.toTypeName(), 0);
        primaryConstructor().flags |= PRIVATE;
        primaryConstructor().setInfo(
            Type.MethodType(Symbol.EMPTY_ARRAY, typeConstructor()));
        this.module = module;
    }

    public ModuleSymbol sourceModule() {
        return module;
    }
}
