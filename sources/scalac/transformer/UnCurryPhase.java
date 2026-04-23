/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.transformer;

import scalac.*;
import scalac.symtab.*;
import scalac.checkers.*;

public class UnCurryPhase extends Phase implements Modifiers {

    /** Initializes this instance. */
    public UnCurryPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
    }

    /** Applies this phase to the given compilation units. */
    public void apply(Unit[] units) {
        for (int i = 0; i < units.length; i++)
            new UnCurry(global, this).apply(units[i]);
    }

    /** - return symbol's transformed type,
     *  - if symbol is a def parameter with transformed type T, return () => T
     */
    public Type transformInfo(Symbol sym, Type tp0) {
	Type tp1 = uncurry(tp0);
	if (sym.isDefParameter()) return global.definitions.FUNCTION_TYPE(Type.EMPTY_ARRAY, tp1);
	else return tp1;
    }

    /** - (ps_1)...(ps_n)T ==> (ps_1,...,ps_n)T
     */
    Type uncurry(Type tp) {
	if (tp instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)tp;
            Symbol[] params = methodType.vparams;
            Type tp1 = methodType.result;
	    Type newtp1 = uncurry(tp1);
	    if (newtp1 instanceof Type.MethodType) {
                Type.MethodType nextMethodType = (Type.MethodType)newtp1;
                Symbol[] params1 = nextMethodType.vparams;
                Type tp2 = nextMethodType.result;
		Symbol[] newparams = new Symbol[params.length + params1.length];
		System.arraycopy(params, 0, newparams, 0, params.length);
		System.arraycopy(params1, 0, newparams, params.length, params1.length);
		return Type.MethodType(newparams, tp2);
	    } else {
		if (newtp1 == tp1) return tp;
		else return Type.MethodType(params, newtp1);
	    }
        }
	if (tp instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)tp;
            Symbol[] tparams = polyType.tparams;
            Type tp1 = polyType.result;
	    Type newtp1 = uncurry(tp1);
	    if (tp1 instanceof Type.MethodType) {
		if (newtp1 == tp1) return tp;
		else return Type.PolyType(tparams, newtp1);
	    } else {
		newtp1 = Type.MethodType(Symbol.EMPTY_ARRAY, newtp1);
		if (tparams.length == 0) return newtp1;
		else return Type.PolyType(tparams, newtp1);
	    }
        }
	if (tp instanceof Type.OverloadedType) {
	    return new Type.Map() {
		public Type apply(Type t) { return uncurry(t); }
	    }.map(tp);
        }
	if (tp instanceof Type.ConstantType) {
            Type base = ((Type.ConstantType)tp).base;
	    return base;
        }
        if (tp instanceof Type.CompoundType) {
            Type.CompoundType compoundType = (Type.CompoundType)tp;
            Type[] parents = compoundType.parts;
            Scope scope = compoundType.members;
            Symbol symbol = tp.symbol();
            if (!symbol.isClass() || symbol.isCompoundSym()) return tp;
            Scope clone = new Scope();
            for (Scope.SymbolIterator i = scope.iterator(true); i.hasNext();) {
                Symbol member = i.next();
                if (isUnaccessedConstant(member)) continue;
                if (member.isCaseFactory() && !member.isModule()) continue;
                clone.enterOrOverload(member);
            }
            return Type.compoundType(parents, clone, symbol);
	}
	return tp;
    }

    boolean isUnaccessedConstant(Symbol symbol) {
        if (!symbol.isTerm()) return false;
        if ((symbol.flags & ACCESSED) != 0) return false;
        Type symbolType = symbol.type();
        if (symbolType instanceof Type.PolyType) {
            Type.PolyType polyType = (Type.PolyType)symbolType;
            if (polyType.result instanceof Type.ConstantType) {
                return polyType.tparams.length == 0;
            }
        }
        if (symbolType instanceof Type.ConstantType) {
            return true;
        }
        return false;
    }

    public Checker[] postCheckers(Global global) {
        return new Checker[] {
            new CheckSymbols(global),
            new CheckTypes(global),
            new CheckOwners(global),
	    new CheckNames(global)
        };
    }
}
