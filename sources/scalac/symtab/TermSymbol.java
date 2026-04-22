/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import ch.epfl.lamp.util.Position;
import scalac.ApplicationError;
import scalac.util.Debug;
import scalac.util.Name;
import scalac.util.Names;

public class TermSymbol extends Symbol {

    private Symbol clazz;

    public TermSymbol(int pos, Name name, Symbol owner, int flags) {
        super(VAL, pos, name, owner, flags);
        assert !name.isTypeName() : this;
    }

    public static TermSymbol define(
        int pos, Name name, Symbol owner, int flags, Scope scope) {
        Scope.Entry e = scope.lookupEntry(name);
        if (e.owner == scope && e.sym.isExternal() && e.sym.kind == VAL) {
            TermSymbol sym = (TermSymbol) e.sym;
            if (sym.isInitialized()) {
                Type symType = sym.type();
                if (symType instanceof Type.OverloadedType) {
                    Symbol[] alts = ((Type.OverloadedType)symType).alts;
                    int i = 0;
                    while (i < alts.length && !alts[i].isExternal())
                        i++;
                    if (i < alts.length) {
                        alts[i].update(pos, flags);
                        if (i == alts.length - 1)
                            sym.update(pos, sym.flags);
                        return (TermSymbol) alts[i];
                    }
                    throw new ApplicationError("TermSymbol.define " + sym);
                }
            }
            sym.update(pos, flags);
            return sym;
        } else {
            return new TermSymbol(pos, name, owner, flags);
        }
    }

    public static TermSymbol newConstructor(Symbol clazz, int flags) {
        TermSymbol sym = new TermSymbol(
            clazz.pos, Names.CONSTRUCTOR, clazz.owner(), flags);
        sym.clazz = clazz;
        return sym;
    }

    public TermSymbol makeConstructor(ClassSymbol clazz) {
        this.clazz = clazz;
        return this;
    }

    public static TermSymbol newJavaConstructor(Symbol clazz) {
        return newConstructor(clazz, clazz.flags & (ACCESSFLAGS | JAVA));
    }

    public TermSymbol makeModule(ClassSymbol clazz) {
        flags |= MODUL | FINAL;
        this.clazz = clazz;
        clazz.setModule(this);
        setInfo(clazz.typeConstructor());
        return this;
    }

    public TermSymbol makeModule() {
        ClassSymbol clazz = new ClassSymbol(
            pos, name.toTypeName(), owner(), flags | MODUL | FINAL);
        clazz.primaryConstructor().setInfo(
            Type.MethodType(Symbol.EMPTY_ARRAY, clazz.typeConstructor()));
        return makeModule(clazz);
    }

    public static TermSymbol newCompanionModule(Symbol clazz, int flags, Type.LazyType parser) {
        TermSymbol sym = new TermSymbol(
            Position.NOPOS, clazz.name.toTermName(), clazz.owner(), flags | STABLE)
            .makeModule();
        sym.clazz.setInfo(parser);
        return sym;
    }

    public static TermSymbol newJavaPackageModule(Name name, Symbol owner, Type.LazyType parser) {
        TermSymbol sym = new TermSymbol(Position.NOPOS, name, owner, JAVA | PACKAGE)
            .makeModule();
        sym.clazz.flags |= SYNTHETIC;
        sym.clazz.setInfo(parser != null ? parser : Type.compoundType(Type.EMPTY_ARRAY, new Scope(), sym));
        return sym;
    }

    public static Symbol newLocalDummy(Symbol clazz) {
        return new TermSymbol(clazz.pos, Names.LOCAL(clazz), clazz, 0)
            .setInfo(Type.NoType);
    }

    public Type thisType() {
        if ((flags & MODUL) != 0) return moduleClass().thisType();
        else return Type.localThisType;
    }

    public Name fullName() {
        if (clazz != null) return clazz.fullName();
        else return super.fullName();
    }

    public boolean isInitializer() {
        return clazz == null && name == Names.CONSTRUCTOR;
    }

    public boolean isConstructor() {
        return clazz != null && name == Names.CONSTRUCTOR;
    }

    public Symbol cloneSymbol(Symbol owner) {
        assert !isPrimaryConstructor() : Debug.show(this);
        TermSymbol other;
        if (isModule()) {
            other = new TermSymbol(pos, name, owner, flags).makeModule();
        } else {
            other = new TermSymbol(pos, name, owner, flags);
            other.clazz = clazz;
        }
        other.setInfo(info());
        return other;
    }

    public Symbol[] typeParams() {
        return type().typeParams();
    }

    public Symbol[] valueParams() {
        return type().valueParams();
    }

    public Symbol constructorClass() {
        return isConstructor() && clazz != null ? clazz : this;
    }

    public Symbol moduleClass() {
        return (flags & MODUL) != 0 ? clazz : this;
    }
}
