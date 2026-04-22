/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import ch.epfl.lamp.util.Position;
import scalac.symtab.classfile.ClassParser;
import scalac.util.Name;
import scalac.util.Names;

public class ClassSymbol extends TypeSymbol {

    private Name mangled;
    private Symbol module = NONE;
    private Symbol thisSym = this;
    public Symbol thisSym() { return thisSym; }
    final private Type thistp = Type.ThisType(this);

    public ClassSymbol(int pos, Name name, Symbol owner, int flags) {
        super(CLASS, pos, name, owner, flags);
        this.mangled = name;
    }

    public static ClassSymbol define(
        int pos, Name name, Symbol owner, int flags, Scope scope) {
        Scope.Entry e = scope.lookupEntry(name);
        if (e.owner == scope && e.sym.isExternal() && e.sym.kind == CLASS) {
            ClassSymbol sym = (ClassSymbol) e.sym;
            sym.update(pos, flags);
            return sym;
        } else {
            return new ClassSymbol(pos, name, owner, flags);
        }
    }

    public ClassSymbol(Name name, Symbol owner, SourceCompleter parser) {
        this(Position.NOPOS, name, owner, 0);
        this.module = TermSymbol.newCompanionModule(this, 0, parser);
        this.setInfo(parser);
    }

    public ClassSymbol(Name name, Symbol owner, ClassParser parser) {
        this(Position.NOPOS, name, owner, JAVA);
        this.module = TermSymbol.newCompanionModule(this, JAVA, parser.staticsParser(this));
        this.setInfo(parser);
    }

    public Symbol cloneSymbol(Symbol owner) {
        ClassSymbol other = new ClassSymbol(pos, name, owner, flags);
        other.module = module;
        other.setInfo(info());
        copyConstructorInfo(other);
        other.mangled = mangled;
        if (thisSym != this) other.setTypeOfThis(typeOfThis());
        return other;
    }

    public void copyTo(Symbol sym) {
        super.copyTo(sym);
        if (thisSym != this) sym.setTypeOfThis(typeOfThis());
    }

    public Symbol module() {
        return module;
    }

    void setModule(Symbol module) { this.module = module; }

    public Symbol setMangledName(Name name) {
        this.mangled = name;
        return this;
    }

    public Name fullName() {
        if (owner().kind == CLASS && !owner().isRoot())
            return Name.fromString(owner().fullName() + "." + name);
        else
            return name.toTermName();
    }

    public Name mangledName() {
        return mangled;
    }

    public Name mangledFullName() {
        if (mangled == name) {
            return fullName().replace((byte)'.', (byte)'$');
        } else {
            Symbol tc = enclToplevelClass();
            if (tc != this) {
                return Name.fromString(
                    enclToplevelClass().mangledFullName() + "$" + mangled);
            } else {
                return mangled;
            }
        }
    }

    public Type thisType() {
        return thistp;
    }

    public Type typeOfThis() {
        return thisSym.type();
    }

    public Symbol setTypeOfThis(Type tp) {
        thisSym = new TermSymbol(this.pos, Names.this_, this, SYNTHETIC);
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

    public void reset(Type completer) {
        super.reset(completer);
        module().reset(completer);
        thisSym = this;
    }
}
