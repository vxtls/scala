/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.transformer;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.Unit;
import scalac.symtab.Definitions;
import scalac.symtab.Scope;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.atree.AConstant;
import scalac.ast.Transformer;
import scalac.ast.GenTransformer;
import scalac.ast.Tree;
import scalac.backend.Primitives;

import scalac.util.Name;
import scalac.util.Names;
import scalac.util.Debug;

/**
 * Turn types into values by applying the following transformations:
 *
 * - For all type member T of all classes, add an accessor method
 *   T$type returnining the type as a value (the accessor is abstract
 *   if the type member is abstract).
 *
 * - For all polymorphic methods/constructors, add a value parameter
 *   for each type parameter.
 *
 * - Add a method getType to every class, to obtain its type as a
 *   value.
 *
 * - Transform all type expressions into value expressions: type
 *   application is turned into value application, type selection into
 *   value selection, and so on.
 *
 * @author Michel Schinz
 * @version 1.0
 */

public class TypesAsValuesPhase extends Phase {
    private final TV_Transformer transformer;

    private static final HashMap/*<Symbol,Map<Symbol,Symbol>>*/ membersToAdd =
        new HashMap();
    private static final HashMap/*<Symbol,Map<Symbol,Symbol>>*/ paramsToAdd =
        new HashMap();
    private static final HashMap/*<Symbol,Symbol>*/ accessors =
        new HashMap();

    private final Definitions defs = global.definitions;
    private final Primitives prims = global.primitives;

    private final Symbol typeClass = defs.TYPE_CLASS;
    private final Type typeType = defs.TYPE_TYPE();
    private final Type.MethodType typeAccessorType =
        new Type.MethodType(new Symbol[]{}, typeType);

    private final Symbol singleTypeClass = defs.SINGLETYPE_CLASS;
    private final Symbol ARRAY_CONSTRUCTOR =
        defs.ARRAY_CLASS.primaryConstructor();

    private final Map/*<Symbol, Symbol>*/ basicTypes;

    public TypesAsValuesPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
        transformer = new TV_Transformer(global);

        basicTypes = new HashMap();
        basicTypes.put(defs.DOUBLE_CLASS,  defs.RTT_DOUBLE());
        basicTypes.put(defs.FLOAT_CLASS,   defs.RTT_FLOAT());
        basicTypes.put(defs.LONG_CLASS,    defs.RTT_LONG());
        basicTypes.put(defs.INT_CLASS,     defs.RTT_INT());
        basicTypes.put(defs.SHORT_CLASS,   defs.RTT_SHORT());
        basicTypes.put(defs.CHAR_CLASS,    defs.RTT_CHAR());
        basicTypes.put(defs.BYTE_CLASS,    defs.RTT_BYTE());
        basicTypes.put(defs.BOOLEAN_CLASS, defs.RTT_BOOLEAN());
    }

    /**
     * Return a map associating symbols for type accessors to the
     * symbol of their type.
     */
    private HashMap typeAccessors(Symbol classSym) {
        HashMap/*<Symbol, Symbol>*/ newSymbols =
            (HashMap)membersToAdd.get(classSym);

        if (newSymbols == null) {
            newSymbols = new HashMap();

            Scope.SymbolIterator membersIt = classSym.members().iterator(true);
            while (membersIt.hasNext()) {
                Symbol member = membersIt.next();
                if (member.isType() /*&& !member.isClass()*/) {
                    Symbol typeAccessorSym = getAccessorSymbol(member);
                    newSymbols.put(member, typeAccessorSym);
                }
            }
            membersToAdd.put(classSym, newSymbols);
        }
        return newSymbols;
    }

    private HashMap typeParams(Symbol funSym) {
        HashMap newSymbols = (HashMap)paramsToAdd.get(funSym);
        if (newSymbols == null) {
            Symbol[] tparams = funSym.typeParams();
            assert tparams.length > 0;

            newSymbols = new HashMap();
            for (int i = 0; i < tparams.length; ++i) {
                Symbol param = tparams[i];
                Symbol valueSym = getAccessorSymbol(param);
                newSymbols.put(param, valueSym);
            }
            paramsToAdd.put(funSym, newSymbols);
        }

        return newSymbols;
    }

    /**
     * Return the symbol of the accessor for the given type symbol.
     */
    private Symbol getAccessorSymbol(Symbol typeSym) {
        assert typeSym.isType();
        Symbol accessorSym = (Symbol)accessors.get(typeSym);
        if (accessorSym == null) {
            accessorSym = typeSym.owner().newVariable(typeSym.pos,
                                                      typeSym.flags,
                                                      Names.TYPE(typeSym));
            accessorSym.setInfo(typeSym.owner().isClass()
                                ? typeAccessorType
                                : typeType);
            accessors.put(typeSym, accessorSym);
        }
        return accessorSym;
    }

    public Type transformInfo(Symbol symbol, Type type) {
        if (symbol.isClass()) {
            // Class:
            // - add an accessor per type member.
            HashMap newSymbols = typeAccessors(symbol);
            if (newSymbols.isEmpty())
                return type;
            else {
                Scope newMembers = new Scope(symbol.members());
                Iterator newSymbolsIt = newSymbols.values().iterator();
                while (newSymbolsIt.hasNext())
                    newMembers.enterOrOverload((Symbol)newSymbolsIt.next());
                return Type.compoundType(type.parents(), newMembers, symbol);
            }
        } else if (type.typeParams().length > 0 && !isPrimitive(symbol)) {
            // Polymorphic method/constructor:
            // - add a value parameter for every type parameter.
            if (type instanceof Type.PolyType) {
                Type.PolyType polyType = (Type.PolyType)type;
                if (polyType.result instanceof Type.MethodType) {
                    Symbol[] tparams = polyType.tparams;
                    Type.MethodType methodType = (Type.MethodType)polyType.result;
                    Symbol[] vparams = methodType.vparams;
                    Type result = methodType.result;
                HashMap newVParams = typeParams(symbol);
                Symbol[] allVParams =
                    new Symbol[newVParams.size() + vparams.length];
                for (int i = 0; i < tparams.length; ++i)
                    allVParams[i] = (Symbol)newVParams.get(tparams[i]);
                System.arraycopy(vparams,
                                 0,
                                 allVParams,
                                 tparams.length,
                                 vparams.length);
                return new Type.PolyType(tparams,
                                         new Type.MethodType(allVParams,
                                                             result));
                }
            }
            throw Debug.abort("unexpected type: ", type);
        } else
            return type;
    }

    private boolean isPrimitive(Symbol sym) {
        throw new Error();      // TODO
    }

    public void apply(Unit[] units) {
        transformer.apply(units);
    }

    private class TV_Transformer extends GenTransformer {
        private Symbol currentOwner;

        public TV_Transformer(Global global) {
            super(global);
        }

        public Tree transform(Tree tree) {
            if (tree instanceof Tree.ClassDef) {
                Tree.ClassDef classDef = (Tree.ClassDef)tree;
                Tree.Template impl = classDef.impl;
                Symbol sym = tree.symbol();
                Tree[] newBody = transform(impl.body, impl.symbol());

                // Add accessors for type members
                Tree[] finalBody;
                HashMap/*<Symbol, Symbol>*/ membersToAdd = typeAccessors(sym);
                if (!membersToAdd.isEmpty()) {
                    finalBody = new Tree[newBody.length + membersToAdd.size()];
                    for (int n = 0, nn = 0; n < newBody.length; ++n) {
                        finalBody[nn++] = newBody[n];
                        Symbol memberSym = newBody[n].symbol();
                        if (membersToAdd.containsKey(memberSym)) {
                            Symbol symToAdd =
                                (Symbol)membersToAdd.get(memberSym);
                            Tree rhs = memberSym.isAbstractType()
                                ? Tree.Empty
                                : typeAsValue(newBody[n].pos,
                                              memberSym.type(),
                                              symToAdd);
                            finalBody[nn++] = gen.DefDef(symToAdd, rhs);
                        }
                    }
                } else
                    finalBody = newBody;

                return gen.ClassDef(sym,
                                    transform(impl.parents),
                                    impl.symbol(),
                                    finalBody);
            } else if (tree instanceof Tree.DefDef) {
                Tree.DefDef defDef = (Tree.DefDef)tree;
                Symbol symbol = getSymbolFor(tree);
                return gen.DefDef(symbol, transform(defDef.rhs, symbol));
            } else if (tree instanceof Tree.ValDef) {
                Tree.ValDef valDef = (Tree.ValDef)tree;
                Symbol symbol = getSymbolFor(tree);
                if (valDef.rhs instanceof Tree.Literal
                    && ((Tree.Literal)valDef.rhs).value == AConstant.ZERO) {
                // transform default values:
                //   val x: T = _
                // becomes
                //   val x: T = asValue(T).defaultValue()
                Tree defaultValue =
                    gen.mkRef(tree.pos,
                              typeAsValue(tree.pos, valDef.tpe.type, currentOwner),
                              defs.TYPE_DEFAULTVALUE());
                Tree rhs = gen.mkApply__(tree.pos, defaultValue);
                return gen.ValDef(symbol, rhs);
                }
                return gen.ValDef(symbol, transform(valDef.rhs, symbol));
            } else if (tree instanceof Tree.New) {
                Tree.Template templ = ((Tree.New)tree).templ;
                Tree[] parents = templ.parents;
                if (parents[0] instanceof Tree.Apply) {
                    Tree.Apply parentApply = (Tree.Apply)parents[0];
                    if (parentApply.fun instanceof Tree.TypeApply) {
                        Tree.TypeApply parentTypeApply = (Tree.TypeApply)parentApply.fun;
                        Tree fun = parentTypeApply.fun;
                        Tree[] targs = parentTypeApply.args;
                        Tree[] vargs = parentApply.args;
                    if (fun.symbol() == ARRAY_CONSTRUCTOR) {
                        // Transform array creations:
                        //   new Array[T](size)
                        // becomes
                        //   asValue(T).newArray[T](size)
                        assert targs.length == 1;
                        assert vargs.length == 1;
                        Tree newArrayfun = gen.mkRef(tree.pos,
                                                     typeAsValue(targs[0].pos,
                                                                 targs[0].type,
                                                                 currentOwner),
                                                     defs.TYPE_NEWARRAY());
                        return gen.mkApplyTV(newArrayfun, targs, vargs);
                    } else
                        return super.transform(tree);
                    }
                }
                return super.transform(tree);
            } else if (tree instanceof Tree.Apply
                       && ((Tree.Apply)tree).fun instanceof Tree.TypeApply) {
                Tree.Apply apply = (Tree.Apply)tree;
                Tree.TypeApply typeApply = (Tree.TypeApply)apply.fun;
                Tree fun = typeApply.fun;
                Tree[] targs = typeApply.args;
                Tree[] vargs = apply.args;
                Symbol funSym = fun.symbol();

                if (funSym == defs.ANY_IS) {
                    // Transform instance tests:
                    //   e.isInstanceOf[T]
                    // becomes:
                    //   asValue(T).hasAsInstance(e)
                    // unless T is a "simple" type for which a Java
                    // instance test is sufficient, in which case the
                    // expression is left as is.
                    assert targs.length == 1 && vargs.length == 0;
                    Tree tp = targs[0];
                    if (isTrivialType(tp.type))
                        return super.transform(tree);
                    else {
                        Tree tpV = typeAsValue(tp.pos, tp.type, currentOwner);
                        Tree hasAsInst =
                            gen.Select(tp.pos, tpV, defs.TYPE_HASASINSTANCE());
                        return gen.mkApply_V(tree.pos,
                                             hasAsInst,
                                             new Tree[] {
                                                 extractQualifier(fun)
                                             });
                    }
                } else if (funSym == defs.ANY_AS) {
                    // Transform instance tests:
                    //   e.asInstanceOf[T]
                    // becomes:
                    //   asValue(T).checkCastability(e).asInstanceOf[T]
                    // unless T is a "simple" type for which a Java
                    // instance test is sufficient, in which case the
                    // expression is left as is.
                    assert targs.length == 1 && vargs.length == 0;
                    return super.transform(tree); // TODO
                } else {
                    // Transform applications to pass types as values:
                    //   f[T1, ...](v1, ...)
                    // becomes
                    //   f[T1, ...](asValue(T1), ..., v1, ...)
                    Tree[] newVArgs = transform(vargs);
                    Tree[] finalVArgs =
                        new Tree[newVArgs.length + targs.length];
                    for (int i = 0; i < targs.length; ++i)
                        finalVArgs[i] = typeAsValue(targs[i].pos,
                                                    targs[i].type,
                                                    currentOwner);
                    System.arraycopy(newVArgs, 0,
                                     finalVArgs, targs.length,
                                     newVArgs.length);
                    return gen.mkApplyTV(tree.pos,
                                         transform(fun),
                                         targs,
                                         finalVArgs);
                }
            }
            return super.transform(tree);
        }

        private Tree transform(Tree tree, Symbol currentOwner) {
            Symbol bkpOwner = this.currentOwner;
            this.currentOwner = currentOwner;
            Tree newTree = transform(tree);
            this.currentOwner = bkpOwner;
            return newTree;
        }

        private Tree[] transform(Tree[] trees, Symbol currentOwner) {
            Symbol bkpOwner = this.currentOwner;
            this.currentOwner = currentOwner;
            Tree[] newTrees = transform(trees);
            this.currentOwner = bkpOwner;
            return newTrees;
        }

        /**
         * Return true iff the given type is "trivial", that is if it
         * is representable as a Java type without loss of
         * information.
         */
        private boolean isTrivialType(Type tp) {
            if (tp instanceof Type.ConstantType) {
                return isTrivialType(((Type.ConstantType)tp).base);
            } else if (tp instanceof Type.TypeRef) {
                Type.TypeRef typeRef = (Type.TypeRef)tp;
                return typeRef.sym.isStatic() && typeRef.args.length == 0;
            } else if (tp instanceof Type.SingleType
                       || tp instanceof Type.ThisType
                       || tp instanceof Type.CompoundType) {
                return false;
            }
            throw Debug.abort("unexpected type", tp);
        }

        /**
         * Transform a type into a tree representing it.
         */
        private Tree typeAsValue(int pos, Type tp, Symbol owner) {
            if (tp instanceof Type.ConstantType) {
                return typeAsValue(pos, ((Type.ConstantType)tp).base, owner);
            } else if (tp instanceof Type.TypeRef) {
                Type.TypeRef typeRef = (Type.TypeRef)tp;
                Type pre = typeRef.pre;
                Symbol sym = typeRef.sym;
                Type[] args = typeRef.args;
                Symbol symOwner = sym.owner();

                if (basicTypes.containsKey(sym)) {
                    return gen.mkRef(pos,
                                     gen.mkGlobalRef(pos, typeClass.module()),
                                     (Symbol)basicTypes.get(sym));
                } else if (symOwner.isClass() || symOwner.isMethod()) {
                    // Reference to a "local" type.
                    Symbol accessor = getAccessorSymbol(sym);
                    if (accessor.isMethod())
                        return gen.mkApply__(gen.mkLocalRef(pos, accessor));
                    else
                        return gen.mkLocalRef(pos, accessor);
                } else {
                    // Reference to a "non-local" type.
                    Tree[] ctorArgs = new Tree[args.length + 2];
                    ctorArgs[0] = symOwner.isPackage()
                        ? gen.mkNullLit(pos)
                        : prefixAsValue(pos, pre);
                    ctorArgs[1] =
                        gen.mkStringLit(pos, prims.getJREClassName(sym));
                    for (int i = 0; i < args.length; ++i)
                        ctorArgs[i + 2] = typeAsValue(pos, args[i], owner);

                    Symbol typeConstr =
                        defs.CONSTRUCTEDTYPE_CTOR(args.length);

                    return gen.New(pos,
                                   gen.mkApply_V(gen.mkGlobalRef(pos,
                                                                 typeConstr),
                                                 ctorArgs));
                }
            } else if (tp instanceof Type.SingleType) {
                Type.SingleType singleType = (Type.SingleType)tp;
                Type pre = singleType.pre;
                Symbol sym = singleType.sym;
                Tree constr =
                    gen.mkPrimaryConstructorGlobalRef(pos, singleTypeClass);
                Tree[] args = new Tree[] { gen.mkRef(pos, pre, sym) };
                return gen.New(pos, gen.mkApply_V(constr, args));
            }
            throw global.fail("unexpected type: ", tp);
        }

        /**
         * Extract qualifier from a tree, which must be a Select node.
         */
        private Tree extractQualifier(Tree tree) {
            if (tree instanceof Tree.Select)
                return ((Tree.Select)tree).qualifier;
            throw Debug.abort("cannot extract qualifier from ", tree);
        }

        /**
         * Transform a prefix into a tree representing it.
         */
        private Tree prefixAsValue(int pos, Type pre) {
            if (pre instanceof Type.ThisType) {
                Symbol clazz = ((Type.ThisType)pre).sym;
                if (clazz.isPackage() || clazz.isNone())
                    return gen.mkNullLit(pos);
                else
                    return gen.This(pos, clazz);
            } else if (pre instanceof Type.SingleType) {
                Type.SingleType singleType = (Type.SingleType)pre;
                Type prefix = singleType.pre;
                Symbol member = singleType.sym;
                return gen.mkApply__(pos,
                                     gen.mkRef(pos,
                                               prefixAsValue(pos, prefix),
                                               member));
            }
            throw Debug.abort("unexpected prefix", pre);
        }
    }
}
