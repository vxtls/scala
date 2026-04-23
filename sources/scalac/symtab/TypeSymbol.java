/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */

package scalac.symtab;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

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
        Type info = primaryConstructor().info().cloneType(
            primaryConstructor(), other.primaryConstructor());
        if (!isTypeAlias()) info = fixConstrType(info, other);
        other.primaryConstructor().setInfo(info);
        Symbol[] alts = allConstructors().alternativeSymbols();
        for (int i = 1; i < alts.length; i++) {
            Symbol constr = other.addConstructor();
            constr.flags = other.flags;
            info = alts[i].info().cloneType(alts[i], constr);
            if (!isTypeAlias()) info = fixConstrType(info, other);
            constr.setInfo(info);
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
            if (typeRef.sym != this && isTypeAlias() && owner().isCompoundSym())
                return type;
            assert typeRef.sym == this : Debug.show(typeRef.sym) + " != " + Debug.show(this);
            return Type.typeRef(typeRef.pre, clone, typeRef.args);
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
        if (closures.limit().id <= phase.id) {
            while (closures.limit() != phase) {
                Phase limit = closures.limit();
                Type[] closure = closures.closure;
                for (int i = 0; i < closure.length; i++) {
                    Symbol symbol = closure[i].symbol();
                    if (symbol.infoAt(limit) != symbol.infoAt(limit.next)) {
                        computeClosureAt(limit.next);
                        break;
                    }
                }
                closures.setLimit(limit.next);
            }
            return closures.closure;
        } else {
            ClosureIntervalList closures = this.closures;
            while (phase.id < closures.start.id && closures.prev != null)
                closures = closures.prev;
            return closures.closure;
        }
    }

    private final void computeClosureAt(Phase phase) {
        Phase current = Global.instance.currentPhase;
        Global.instance.currentPhase = phase;
        Map parents = inclClosure(new TreeMap(comparator), info());
        Type[] closure = new Type[parents.size() + 1];
        closures = new ClosureIntervalList(closures, closure, phase);
        // The next put needs a defined closure size because of isLess.
        parents.put(this, type());
        parents.values().toArray(closure);
        Global.instance.currentPhase = current;
    }

    private static Map inclClosure(Map closure, Type type) {
        type = type.unalias();
        if (type == Type.ErrorType) {
            return closure;
        } else if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            Type.Map map = Type.getThisTypeMap(typeRef.sym, type);
            Type[] parents = typeRef.sym.closure();
            for (int i = 0; i < parents.length; i++)
                closure.put(parents[i].symbol(), map.apply(parents[i]));
            return closure;
        } else if (type instanceof Type.CompoundType) {
            Type[] parents = ((Type.CompoundType)type).parts;
            for (int i = 0; i < parents.length; i++)
                inclClosure(closure, parents[i]);
            return closure;
        }
        throw Debug.abort("illegal case", type);
    }

    private static Comparator comparator = new Comparator() {
        public int compare(Object lf, Object rg) {
            if (lf == rg) return 0;
            return ((Symbol)lf).isLess((Symbol)rg) ? -1 : 1;
        }
    };

    public void reset(Type completer) {
        super.reset(completer);
        closures = null;
        tycon = null;
    }
}
