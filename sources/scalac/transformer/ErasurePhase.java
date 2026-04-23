/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: ErasurePhase.java,v 1.13 2002/11/14 15:58:22 schinz Exp $
// $Id$

package scalac.transformer;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.Unit;
import scalac.backend.Primitive;
import scalac.backend.Primitives;
import scalac.checkers.Checker;
import scalac.checkers.CheckOwners;
import scalac.checkers.CheckSymbols;
import scalac.checkers.CheckTypes;
import scalac.checkers.CheckNames;
import scalac.symtab.AbsTypeSymbol;
import scalac.symtab.Definitions;
import scalac.symtab.Modifiers;
import scalac.symtab.Scope;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Name;
import scalac.util.Debug;

public class ErasurePhase extends Phase {

    //########################################################################
    // Private Fields

    private final Definitions definitions;
    private final Primitives primitives;
    private final Erasure erasure;

    //########################################################################
    // Public Constructors

    public ErasurePhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
        this.definitions = global.definitions;
        this.primitives = global.primitives;
        this.erasure = new Erasure(global);
    }

    //########################################################################
    // Public Methods

    public void apply(Unit[] units) {
        erasure.apply(units);
    }

    public Type transformInfo(Symbol sym, Type tp) {
        if (sym.isConstructor() && sym.constructorClass().isSubClass(definitions.ANYVAL_CLASS)) return tp;
        if (sym.isClass()) {
            if (sym == definitions.ANY_CLASS) return tp;
            if (sym.isJava() && sym.isModuleClass()) return tp;
            if (sym.isSubClass(definitions.ANYVAL_CLASS))
                if (sym != definitions.ANYVAL_CLASS) return tp;
            if (tp instanceof Type.CompoundType) {
                Type.CompoundType compoundType = (Type.CompoundType)tp;
                Type[] parents = compoundType.parts;
                Scope members = compoundType.members;
                assert parents.length != 0: Debug.show(sym) + " -- " + tp;
                if (sym.isInterface()) {
                    Symbol superclass = parents[0].symbol();
                    if (superclass.isJava() && !superclass.isInterface()) {
                        parents = Type.cloneArray(parents);
                        parents[0] = definitions.ANYREF_TYPE();
                        tp = Type.compoundType(parents, members, sym);
                    }
                }
                return Type.erasureMap.map(tp);
            }
            throw Debug.abort("illegal case", tp);
        }
        if (sym.isTerm() && sym.isParameter()) {
            if (primitives.getPrimitive(sym.owner()) == Primitive.BOX)
                return eraseUnboxMethodType(tp);
            if (primitives.getPrimitive(sym.owner()) == Primitive.UNBOX)
                return eraseBoxMethodType(tp);
        }
        if (sym.isType()) return tp;
        if (sym.isThisSym()) return sym.owner().nextType();
        // if (sym == definitions.NULL) return tp.resultType().erasure();
        if (global.target == global.TARGET_INT && sym == primitives.NEW_OARRAY) {
            // Keep the polymorphic interpreter entrypoint from 1.1.0-b4.
            Name name = Name.fromString("element").toTypeName();
            Symbol tparam = new AbsTypeSymbol(0, name, sym, Modifiers.PARAM);
            tparam.setType(definitions.ANY_TYPE());
            return Type.PolyType(new Symbol[]{tparam}, tp);
        }
        switch (primitives.getPrimitive(sym)) {
        case IS: return Type.PolyType(tp.typeParams(), Type.MethodType(tp.valueParams(), tp.resultType().erasure()));
        case AS: return tp;
        case BOX: return eraseBoxMethodType(tp);
        case UNBOX: return eraseUnboxMethodType(tp);
        default: return tp.erasure();
        }
    }

    public Checker[] postCheckers(Global global) {
        return new Checker[] {
            new CheckSymbols(global),
            new CheckTypes(global),
            new CheckOwners(global),
            new CheckNames(global)
        };
    }

    //########################################################################
    // Private Methods

    private Type eraseBoxMethodType(Type type) {
        if (type instanceof Type.PolyType) {
            return eraseBoxMethodType(((Type.PolyType)type).result);
        } else if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            return Type.MethodType(methodType.vparams, eraseBoxMethodType(methodType.result));
        } else if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            return Type.typeRef(typeRef.pre, typeRef.sym, Type.EMPTY_ARRAY);
        }
        throw Debug.abort("illegal case", type);
    }

    private Type eraseUnboxMethodType(Type type) {
        if (type instanceof Type.PolyType) {
            return eraseUnboxMethodType(((Type.PolyType)type).result);
        } else if (type instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)type;
            return Type.MethodType(methodType.vparams, eraseUnboxMethodType(methodType.result));
        } else if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            if (typeRef.sym == definitions.ARRAY_CLASS) {
                Symbol element = typeRef.args[0].symbol();
                if (element.isAbstractType())
                    if (element.info().symbol() == definitions.ANY_CLASS)
                        return definitions.ANYREF_CLASS.nextType();
            }
            return type.fullErasure();
        }
        throw Debug.abort("illegal case", type);
    }

    //########################################################################
}
