/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import scalac.Global;
import scalac.Phase;
import scalac.util.ArrayApply;
import scalac.util.Debug;
import scalac.util.Name;

public abstract class TypeSymbol extends Symbol {

    private ClosureIntervalList closures;
    private Type tycon = null;
    private Symbol constructor;

    public TypeSymbol(int kind, int pos, Name name, Symbol owner, int flags) {
        super(kind, pos, name, owner, flags);
        assert name.isTypeName() : this;
        this.constructor = TermSymbol.newConstructor(this, flags & CONSTRFLAGS);
    }

    protected void update(int pos, int flags) {
        super.update(pos, flags);
        constructor.pos = pos;
    }

    public void copyTo(Symbol sym) {
        super.copyTo(sym);
        Symbol symconstr = ((TypeSymbol) sym).constructor;
        constructor.copyTo(symconstr);
        if (constructor.isInitialized())
            symconstr.setInfo(fixConstrType(symconstr.type(), sym));
    }

    protected final void copyConstructorInfo(TypeSymbol other) {
        other.primaryConstructor().setInfo(
            fixConstrType(
                primaryConstructor().info().cloneType(
                    primaryConstructor(), other.primaryConstructor()),
                other));
        Symbol[] alts = allConstructors().alternativeSymbols();
        for (int i = 1; i < alts.length; i++) {
            Symbol constr = other.addConstructor();
            constr.flags = other.flags;
            constr.setInfo(
                fixConstrType(
                    alts[i].info().cloneType(alts[i], constr),
                    other));
        }
    }

    private final Type fixConstrType(Type type, Symbol clone) {
        if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            Type result = fixConstrType(methodType.result, clone);
            return new Type.MethodType(methodType.vparams, result);
        } else if (type instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)type;
            Type result = fixConstrType(polyType.result, clone);
            return new Type.PolyType(polyType.tparams, result);
        } else if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            assert typeRef.sym == this : Debug.show(typeRef.sym) + " != " + Debug.show(this);
            return new Type.TypeRef(typeRef.pre, clone, typeRef.args);
        } else if (type instanceof Type.LazyType) {
            return type;
        }
        throw Debug.abort("unexpected constructor type:" + clone + ":" + type);
    }

    public final Symbol addConstructor() {
        return addConstructor(0);
    }

    public final Symbol addConstructor(int flags) {
        Symbol constr = TermSymbol.newConstructor(
            this,
            (this.flags & CONSTRFLAGS) | (flags & ACCESSFLAGS));
        constructor = constructor.overloadWith(constr);
        return constr;
    }

    public final Symbol primaryConstructor() {
        return constructor.firstAlternative();
    }

    public final Symbol allConstructors() {
        return constructor;
    }

    public final Symbol[] typeParams() {
        return primaryConstructor().info().typeParams();
    }

    public final Symbol[] valueParams() {
        return (kind == CLASS) ? primaryConstructor().info().valueParams()
            : Symbol.EMPTY_ARRAY;
    }

    public final Type typeConstructor() {
        if (tycon == null)
            tycon = Type.TypeRef(owner().thisType(), this, Type.EMPTY_ARRAY);
        return tycon;
    }

    public Symbol setOwner(Symbol owner) {
        tycon = null;
        constructor.setOwner0(owner);
        Type constructorType = constructor.type();
        if (constructorType instanceof Type.OverloadedType) {
            Symbol[] alts = ((Type.OverloadedType)constructorType).alts;
            for (int i = 0; i < alts.length; i++) alts[i].setOwner0(owner);
        }
        return super.setOwner(owner);
    }

    public final Type type() {
        return primaryConstructor().type().resultType();
    }

    public final Type getType() {
        return primaryConstructor().type().resultType();
    }

    public final Type[] closure() {
        if (kind == ALIAS) return info().symbol().closure();
        if (closures == null) computeClosureAt(rawFirstInfoStartPhase());
        Phase phase = Global.instance.currentPhase;
        if (closures.limit().precedes(phase)) {
            while (closures.limit().next != phase) {
                Phase limit = closures.limit().next;
                Type[] closure = closures.closure;
                for (int i = 0; i < closure.length; i++) {
                    Symbol symbol = closure[i].symbol();
                    if (symbol.infoAt(limit) != symbol.infoAt(limit.next)) {
                        computeClosureAt(limit.next);
                        break;
                    }
                }
                closures.setLimit(limit);
            }
            return closures.closure;
        } else {
            ClosureIntervalList closures = this.closures;
            while (!closures.start.precedes(phase) && closures.prev != null)
                closures = closures.prev;
            return closures.closure;
        }
    }

    private final void computeClosureAt(Phase phase) {
        Phase current = Global.instance.currentPhase;
        Global.instance.currentPhase = phase;
        Type[] parents = type().parents();
        assert (flags & LOCKED) == 0 : Debug.show(this) + " -- " + phase;
        flags |= LOCKED;
        SymSet closureClassSet = inclClosure(SymSet.EMPTY, parents);
        flags &= ~LOCKED;
        Symbol[] closureClasses = new Symbol[closureClassSet.size() + 1];
        closureClasses[0] = this;
        closureClassSet.copyToArray(closureClasses, 1);
        closures = new ClosureIntervalList(closures, Symbol.type(closureClasses), phase.prev == null ? phase : phase.prev);

        adjustType(type());
        Global.instance.currentPhase = current;
    }

    private static SymSet inclClosure(SymSet set, Type[] tps) {
        for (int i = 0; i < tps.length; i++) set = inclClosure(set,tps[i]);
        return set;
    }

    private static SymSet inclClosure(SymSet set, Type tp) {
        tp = tp.unalias();
        if (tp instanceof Type.CompoundType) {
            return inclClosure(set, ((Type.CompoundType)tp).parts);
        }
        return inclClosure(set, tp.symbol());
    }

    private static SymSet inclClosure(SymSet set, Symbol sym) {
        while (sym.kind == ALIAS) sym = sym.info().symbol();
        return inclClosure(set.incl(sym), sym.type().parents());
    }

    private void adjustType(Type tp) {
        Type tp1 = tp.unalias();
        if (!(tp instanceof Type.CompoundType)) {
            Symbol sym = tp1.symbol();
            int pos = closurePos(sym);
            assert pos >= 0 : this + " " + tp1 + " " + tp1.symbol() + " " + pos;
            closures.closure[pos] = tp1;
        }
        Type[] parents = tp1.parents();
        for (int i = 0; i < parents.length; i++) {
            adjustType(parents[i]);
        }
    }

    public void reset(Type completer) {
        super.reset(completer);
        closures = null;
        tycon = null;
    }
}
