/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.symtab;

import scalac.Global;
import scalac.framework.History;
import scalac.util.Debug;
import scalac.util.Name;

public abstract class TypeSymbol extends Symbol {

    private final History closures;
    private Type tycon = null;
    private Symbol constructor;

    public TypeSymbol(int kind, Symbol owner, int pos, int flags, Name name, int attrs) {
        super(kind, owner, pos, flags, name, attrs);
        this.closures = new ClosureHistory();
        assert name.isTypeName() : this;
        this.constructor = newConstructor(pos, flags & CONSTRFLAGS);
    }

    protected final void copyConstructorInfo(TypeSymbol other) {
        {
            Type info = primaryConstructor().info().cloneType(
                primaryConstructor(), other.primaryConstructor());
            if (!isTypeAlias()) info = fixConstrType(info, other);
            other.primaryConstructor().setInfo(info);
        }
        Symbol[] alts = allConstructors().alternativeSymbols();
        for (int i = 1; i < alts.length; i++) {
            Symbol constr = other.newConstructor(alts[i].pos, alts[i].flags);
            other.addConstructor(constr);
            Type info = alts[i].info().cloneType(alts[i], constr);
            if (!isTypeAlias()) info = fixConstrType(info, other);
            constr.setInfo(info);
        }
    }

    private Type fixConstrType(Type type, Symbol clone) {
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

    public final void addConstructor(Symbol constr) {
        assert constr.isConstructor(): Debug.show(constr);
        constructor = constructor.overloadWith(constr);
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
            tycon = Type.typeRef(owner().thisType(), this, Type.EMPTY_ARRAY);
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
        if ((flags & CLOSURELOCK) != 0 &&
            Global.instance.currentPhase.id <= Global.instance.PHASE.REFCHECK.id()) {
            throw new Type.Error("illegal cyclic reference involving " + this);
        }
        flags |= CLOSURELOCK;
        Type[] result = (Type[])closures.getValue(this);
        flags &= ~CLOSURELOCK;
        return result;
    }

    public void reset(Type completer) {
        super.reset(completer);
        closures.reset();
        tycon = null;
    }

    protected final Symbol cloneSymbolImpl(Symbol owner, int attrs) {
        TypeSymbol clone = cloneTypeSymbolImpl(owner, attrs);
        copyConstructorInfo(clone);
        return clone;
    }

    protected abstract TypeSymbol cloneTypeSymbolImpl(Symbol owner, int attrs);
}
