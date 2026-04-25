/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

//package scala.compiler.backend;
package scalac.transformer;

import java.util.HashMap;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.CompilationUnit;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.util.Debug;

public class AddConstructorsPhase extends Phase {

    //########################################################################
    // Private Fields

    /** A maps from old constructor symbols to new ones */
    private final HashMap/*<Symbol,Symbol>*/ constructors = new HashMap();

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
    public AddConstructorsPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
    }

    //########################################################################
    // Public Methods

    /** Applies this phase to the given type for the given symbol. */
    public Type transformInfo(Symbol symbol, Type type) {
        if (symbol.isConstructor()) {
            if (type instanceof Type.PolyType) {
                Type.PolyType polyType = (Type.PolyType)type;
                if (polyType.result instanceof Type.MethodType) {
                    Type.MethodType methodType = (Type.MethodType)polyType.result;
                    Type result = Type.MethodType(Symbol.EMPTY_ARRAY, methodType.result);
                    return Type.PolyType(polyType.tparams, result);
                }
            } else if (type instanceof Type.MethodType) {
                return Type.MethodType(Symbol.EMPTY_ARRAY, ((Type.MethodType)type).result);
            }
            throw Debug.abort("illegal case", type);
        }
        return type;
    }

    /** Applies this phase to the given compilation units. */
    public void apply(CompilationUnit[] units) {
        for (int i = 0; i < units.length; i++)
            new AddConstructors(global, constructors).apply(units[i]);
    }

    //########################################################################
}
