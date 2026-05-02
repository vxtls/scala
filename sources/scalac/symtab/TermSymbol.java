/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scalac.util.Debug;
import scalac.util.Name;
import scalac.util.Names;

class TermSymbol extends Symbol {

    TermSymbol(Symbol owner, int pos, int flags, Name name, int attrs) {
        super(VAL, owner, pos, flags, name, attrs);
        assert name.isTermName(): Debug.show(this);
    }

    public boolean isInitializer() {
        return name == Names.INITIALIZER;
    }

    public Symbol[] typeParams() {
        return type().typeParams();
    }

    public Symbol[] valueParams() {
        return type().valueParams();
    }

    protected Symbol cloneSymbolImpl(Symbol owner, int attrs) {
        return new TermSymbol(owner, pos, flags, name, attrs);
    }

    public Symbol overloadWith(Symbol that) {
        assert this.name == that.name : Debug.show(this) + " <> " + Debug.show(that);
        assert this.isConstructor() == that.isConstructor();

        int overflags = (this.flags & that.flags &
                         (JAVA | ACCESSFLAGS | DEFERRED | PARAM | SYNTHETIC)) |
            ((this.flags | that.flags) & ACCESSOR);
        Symbol overloaded = this.isConstructor()
            ? this.constructorClass().newConstructor(this.constructorClass().pos, overflags)
            : owner().newTerm(pos, overflags, name, 0);
        overloaded.setInfo(new LazyOverloadedType(this, that));
        return overloaded;
    }
}
