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
import scalac.util.Names;

public final class TermSymbol extends Symbol {

    /**
     * The module class if this is a module, the constructed class if
     * this is a constructor and null otherwise.
     *
     * This stays mutable because UnPickle wires constructor alternatives
     * to their owning class after the term symbol is created.
     */
    private Symbol clasz;

    TermSymbol(int pos, Name name, Symbol owner, int flags, int attrs, Symbol clasz) {
        super(VAL, pos, name, owner, flags, attrs);
        this.clasz = clasz;
        assert name.isTermName() : Debug.show(this);
    }

    public TermSymbol makeConstructor(ClassSymbol clazz) {
        assert name == Names.CONSTRUCTOR : Debug.show(this);
        this.clasz = clazz;
        return this;
    }

    public boolean isInitializer() {
        return clasz == null && name == Names.INITIALIZER;
    }

    public boolean isConstructor() {
        return clasz != null && name == Names.CONSTRUCTOR;
    }

    public Symbol[] typeParams() {
        return type().typeParams();
    }

    public Symbol[] valueParams() {
        return type().valueParams();
    }

    public Symbol constructorClass() {
        return isConstructor() ? clasz : this;
    }

    public Symbol moduleClass() {
        return isModule() ? clasz : this;
    }

    protected final Symbol cloneSymbolImpl(Symbol owner, int attrs) {
        assert !isPrimaryConstructor() : Debug.show(this);
        return new TermSymbol(pos, name, owner, flags, attrs, clasz);
    }
}
