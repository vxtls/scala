/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scala.tools.util.Position;

import scalac.Global;
import scalac.util.Debug;
import scalac.util.Name;
import scalac.util.Names;

public final class ClassSymbol extends TypeSymbol {

    /**
     * The dual class of this class or NONE. The dual class is:
     * - the corresponding module class if this is a value class
     * - the corresponding value class if this is a module class
     */
    private final Symbol dual;

    /** The module belonging to the class or NONE. */
    private final Symbol module;

    /** The given type of self, or NoType, if no explicit type was given. */
    private Symbol thisSym = this;
    public Symbol thisSym() { return thisSym; }

    /** A cache for this.thisType(). */
    private final Type thistp = Type.ThisType(this);

    private final Symbol rebindSym;

    ClassSymbol(int pos, Name name, Symbol owner, int flags, int attrs, Symbol dual) {
        super(CLASS, pos, name, owner, flags, attrs);
        this.rebindSym = owner.newTypeAlias(pos, 0, Names.ALIAS(this));
        Type rebindType = new ClassAliasLazyType();
        this.rebindSym.setInfo(rebindType);
        this.rebindSym.primaryConstructor().setInfo(rebindType);
        this.module = isModuleClass() ? newModule() : NONE;
        this.dual = dual == null ? newModuleClass() : dual;
    }

    private class ClassAliasLazyType extends Type.LazyType {
        public void complete(Symbol ignored) {
            Symbol clasz = ClassSymbol.this;
            Symbol alias = rebindSym;
            Type prefix = clasz.owner().thisType();
            Type constrtype = clasz.type();
            constrtype = Type.MethodType(Symbol.EMPTY_ARRAY, constrtype);
            constrtype = Type.PolyType(clasz.typeParams(), constrtype);
            constrtype = constrtype.cloneType(
                clasz.primaryConstructor(), alias.primaryConstructor());
            alias.primaryConstructor().setInfo(constrtype);
            alias.setInfo(constrtype.resultType());
        }
    }

    public static Symbol newRootClass(Global global) {
        int pos = Position.NOPOS;
        Name name = Names.ROOT.toTypeName();
        Symbol owner = Symbol.NONE;
        int flags = JAVA | PACKAGE | FINAL | SYNTHETIC;
        int attrs = IS_ROOT;
        Symbol clasz = new ClassSymbol(pos, name, owner, flags, attrs, NONE);
        clasz.setInfo(global.getRootLoader());
        clasz.primaryConstructor().setInfo(
            Type.MethodType(Symbol.EMPTY_ARRAY, clasz.typeConstructor()));
        return clasz;
    }

    private Symbol newThisType() {
        return newTerm(pos, SYNTHETIC, Names.this_, IS_THISTYPE);
    }

    Symbol newModule() {
        assert isModuleClass() : Debug.show(this);
        int flags = (this.flags & CLASS2MODULEFLAGS) | MODUL | FINAL | STABLE;
        Name name = this.name.toTermName();
        Symbol module = new TermSymbol(pos, name, owner(), flags, 0, this);
        module.setType(typeConstructor());
        return module;
    }

    ClassSymbol newModuleClass() {
        assert !isModuleClass() : Debug.show(this);
        return owner().newModuleClass(pos, flags, name, 0, this);
    }

    public Symbol module() {
        assert !isRoot() : this + ".module()";
        return module;
    }

    public Symbol dualClass() {
        return dual;
    }

    public Type thisType() {
        Global global = Global.instance;
        if (global.currentPhase.id > global.PHASE.ERASURE.id()) return type();
        return thistp;
    }

    public Type typeOfThis() {
        return thisSym.type();
    }

    public Symbol setTypeOfThis(Type tp) {
        thisSym = newThisType();
        thisSym.setInfo(tp);
        return this;
    }

    public Symbol enclClass() {
        return this;
    }

    public Symbol caseFieldAccessor(int index) {
        assert (flags & CASE) != 0 : this;
        Scope.SymbolIterator it = info().members().iterator();
        Symbol sym = null;
        if ((flags & JAVA) == 0) {
            for (int i = 0; i <= index; i++) {
                do {
                    sym = it.next();
                } while (sym.kind != VAL || (sym.flags & CASEACCESSOR) == 0 || !sym.isMethod());
            }
        } else {
            sym = it.next();
            while ((sym.flags & SYNTHETIC) == 0) {
                sym = it.next();
            }
            for (int i = 0; i < index; i++)
                sym = it.next();
        }
        assert sym != null : this;
        return sym;
    }

    public final Symbol rebindSym() {
        return rebindSym;
    }

    public void reset(Type completer) {
        super.reset(completer);
        module().reset(completer);
        thisSym = this;
    }

    protected TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs) {
        assert !isModuleClass() : Debug.show(this);
        ClassSymbol clone = new ClassSymbol(pos, name, owner, flags, attrs, NONE);
        if (thisSym != this) clone.setTypeOfThis(typeOfThis());
        return clone;
    }
}
