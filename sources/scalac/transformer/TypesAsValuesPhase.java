/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.transformer;

import scalac.Global;
import scalac.Phase;
import scalac.PhaseDescriptor;
import scalac.CompilationUnit;
import scalac.symtab.Definitions;
import scalac.symtab.Scope;
import scalac.symtab.Symbol;
import scalac.symtab.SymbolNameWriter;
import scalac.symtab.Type;
import scalac.symtab.Modifiers;
import scalac.atree.AConstant;
import scalac.ast.Transformer;
import scalac.ast.GenTransformer;
import scalac.ast.Tree;
import scalac.ast.TreeList;
import scalac.backend.Primitives;

import scalac.util.Name;
import scalac.util.Names;
import scalac.util.Debug;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

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

// TODO use a constant instead of generating empty arrays all the
// time.

public class TypesAsValuesPhase extends Phase {
    private final TV_Transformer transformer;

    /**
     * The list of members to add to a given class (either type
     * accessors or instantiation methods).
     */
    private final HashMap/*<Symbol,NewMember[]>*/ membersToAdd =
        new HashMap();

    /** The list of parameters to add to a given method. */
    private final HashMap/*<Symbol,List<Symbol>>*/ paramsToAdd =
        new HashMap();

    /** The accessor method corresponding to a given type (or class) member. */
    private final HashMap/*<Symbol, Symbol>*/ typeAccessor =
        new HashMap();

    /** The instanciation method corresponding to a given class. */
    private final HashMap/*<Symbol,Symbol>*/ instantiator =
        new HashMap();

    /** The class constructor corresponding to a given class. */
    private final HashMap/*<Symbol,Symbol>*/ classInitialiser =
        new HashMap();

    private final HashMap/*<Symbol,Symbol>*/ tConstructor =
        new HashMap();

    private final Definitions defs = global.definitions;
    private final Primitives prims = global.primitives;

    private final Type.MethodType typeAccessorType =
        new Type.MethodType(new Symbol[]{}, defs.TYPE_TYPE());

    private final Symbol ARRAY_CONSTRUCTOR =
        defs.ARRAY_CLASS.primaryConstructor();

    private final TEnv EENV = new TEnv();

    private final Map/*<Symbol, Symbol>*/ predefTypes;

    private HashMap/*<Symbol, Ancestor[][]>*/ ancestorCache = new HashMap();

    public TypesAsValuesPhase(Global global, PhaseDescriptor descriptor) {
        super(global, descriptor);
        transformer = new TV_Transformer(global);

        predefTypes = new HashMap();
        predefTypes.put(defs.DOUBLE_CLASS,  defs.RTT_DOUBLE());
        predefTypes.put(defs.FLOAT_CLASS,   defs.RTT_FLOAT());
        predefTypes.put(defs.LONG_CLASS,    defs.RTT_LONG());
        predefTypes.put(defs.INT_CLASS,     defs.RTT_INT());
        predefTypes.put(defs.SHORT_CLASS,   defs.RTT_SHORT());
        predefTypes.put(defs.CHAR_CLASS,    defs.RTT_CHAR());
        predefTypes.put(defs.BYTE_CLASS,    defs.RTT_BYTE());
        predefTypes.put(defs.BOOLEAN_CLASS, defs.RTT_BOOLEAN());
        predefTypes.put(defs.UNIT_CLASS,    defs.RTT_UNIT());
        predefTypes.put(defs.ANY_CLASS,     defs.RTT_ANY());
        predefTypes.put(defs.ANYVAL_CLASS,  defs.RTT_ANYVAL());
        predefTypes.put(defs.ALLREF_CLASS,  defs.RTT_ALLREF());
        predefTypes.put(defs.ALL_CLASS,     defs.RTT_ALL());

        membersToAdd.put(defs.ARRAY_CLASS, new NewMember[0]);
        paramsToAdd.put(ARRAY_CONSTRUCTOR, new Symbol[0]);

        ancestorCache.put(defs.OBJECT_CLASS,
                          new Ancestor[][] {
                              new Ancestor[] {
                                  new Ancestor(defs.OBJECT_CLASS, -1, -1)
                              }
                          });
    }

    /**
     * Return the symbol of the accessor for the given type symbol.
     */
    private Symbol getAccessorSym(Symbol typeSym) {
        assert typeSym.isType();
        Symbol accessorSym = (Symbol)typeAccessor.get(typeSym);
        if (accessorSym == null) {
            accessorSym = typeSym.owner().newVariable(typeSym.pos,
                                                      typeSym.flags,
                                                      Names.TYPE(typeSym));
            accessorSym.setInfo(defs.TYPE_TYPE());
            typeAccessor.put(typeSym, accessorSym);
        }
        return accessorSym;
    }

    private Symbol getInstMethSym(Symbol classSym) {
        Symbol imSym = (Symbol)instantiator.get(classSym);
        if (imSym == null) {
            int pos = classSym.pos;
            boolean isStatic = !isNestedClass(classSym);
            Name imName = Names.INSTANTIATE(classSym, isStatic);

            int flags = classSym.isAbstractType() ? Modifiers.DEFERRED : 0;

            imSym = isStatic
                ? classSym.newStaticMethod(pos, 0, imName)
                : classSym.owner().newMethodOrFunction(pos, flags, imName);

            // TODO special case for monomorphic instantiations
            Symbol[] argTypes;
            if (true || classSym.typeParams().length > 0) {
                Symbol typesP =
                    imSym.newVParam(pos, 0, Name.fromString("types"));
                typesP.setInfo(defs.ARRAY_TYPE(defs.TYPE_TYPE()));
                argTypes = new Symbol[]{ typesP };
            } else
                argTypes = Symbol.EMPTY_ARRAY;

            imSym.setInfo(new Type.MethodType(argTypes,
                                              isStatic
                                              ? defs.SCALACLASSTYPE_TYPE()
                                              : defs.TYPE_TYPE()));

            instantiator.put(classSym, imSym);
        }
        return imSym;
    }

    private Symbol getTConstructorSym(Symbol classSym) {
        Symbol tcSym = (Symbol)tConstructor.get(classSym);
        if (tcSym == null) {
            int pos = classSym.pos;
            boolean isStatic = !isNestedClass(classSym);
            Name tcName = Names.TYPECONSTRUCTOR(classSym, isStatic);

            tcSym = isStatic
                ? classSym.newStaticField(pos, 0, tcName)
                : classSym.owner().newFieldOrVariable(pos, 0, tcName);
            tcSym.setInfo(defs.TYPECONSTRUCTOR_TYPE());

            tConstructor.put(classSym, tcSym);
        }
        return tcSym;
    }

    private Symbol getClassInitSym(Symbol classSym) {
        Symbol ciSym = (Symbol)classInitialiser.get(classSym);
        if (ciSym == null) {
            int pos = classSym.pos;

            ciSym = classSym.newStaticMethod(pos, 0, Names.CLASS_CONSTRUCTOR);
            ciSym.setInfo(new Type.MethodType(Symbol.EMPTY_ARRAY,
                                              defs.UNIT_TYPE()));

            classInitialiser.put(classSym, ciSym);
        }
        return ciSym;
    }

    private NewMember[] membersToAdd(Symbol classSym) {
        NewMember[] toAdd = (NewMember[])membersToAdd.get(classSym);
        if (toAdd == null) {
            HashSet seenMembers = new HashSet();
            ArrayList toAddL = new ArrayList();
            Scope.SymbolIterator membersIt = classSym.members().iterator();
            while (membersIt.hasNext()) {
                Symbol member = membersIt.next();
                if (member.isModule())
                    member = member.moduleClass();
                if (!seenMembers.add(member))
                    continue;
                if (member.isClass()) {
                    Symbol tcSym = getTConstructorSym(member);
                    toAddL.add(new NewMember.TypeConstructor(member, tcSym));
                    Symbol imSym = getInstMethSym(member);
                    toAddL.add(new NewMember.Instantiator(member, imSym));
                } else if (member.isType()) {
                    Symbol accSym = getInstMethSym(member);
                    toAddL.add(new NewMember.TypeAccessor(member, accSym));
                }
            }

            if (!isNestedClass(classSym)) {
                Symbol tcSym = getTConstructorSym(classSym);
                toAddL.add(new NewMember.TypeConstructor(classSym, tcSym));
                Symbol ciSym = getClassInitSym(classSym);
                toAddL.add(new NewMember.ClassInitialiser(classSym, ciSym, tcSym));
                Symbol imSym = getInstMethSym(classSym);
                toAddL.add(new NewMember.Instantiator(classSym, imSym));
            }

            toAdd = (NewMember[])toAddL.toArray(new NewMember[toAddL.size()]);
            membersToAdd.put(classSym, toAdd);
        }
        return toAdd;
    }

    private Symbol[] paramsToAdd(Symbol methSym) {
        Symbol[] toAdd = (Symbol[])paramsToAdd.get(methSym);
        if (toAdd == null) {
            Symbol[] tparams = methSym.typeParams();

            ArrayList toAddL = new ArrayList();
            for (int i = 0; i < tparams.length; ++i)
                toAddL.add(getAccessorSym(tparams[i]));

            toAdd = (Symbol[])toAddL.toArray(new Symbol[toAddL.size()]);
            paramsToAdd.put(methSym, toAdd);
        }

        return toAdd;
    }

    public Type transformInfo(Symbol symbol, Type type) {
        if (symbol.isClass()) {
            NewMember[] toAdd = membersToAdd(symbol);

            if (toAdd.length == 0)
                return type;
            else {
                Scope newMembers = new Scope(symbol.members());

                for (int i = 0; i < toAdd.length; ++i)
                    newMembers.enterOrOverload(toAdd[i].symbolToAdd());

                return Type.compoundType(type.parents(), newMembers, symbol);
            }
        } else if (type.typeParams().length > 0 && !isPrimitive(symbol)) {
            // Polymorphic method/constructor:
            // - add a value parameter for every type parameter.
            if (type instanceof Type.PolyType) {
                Type.PolyType polyType = (Type.PolyType)type;
                Symbol[] tparams = polyType.tparams;
                if (polyType.result instanceof Type.MethodType) {
                    Type.MethodType methodType = (Type.MethodType)polyType.result;
                    Symbol[] vparams = methodType.vparams;
                    Type result = methodType.result;
                List newVParams =
                    new LinkedList(Arrays.asList(paramsToAdd(symbol)));
                newVParams.addAll(Arrays.asList(vparams));
                Symbol[] newVParamsA = (Symbol[])
                    newVParams.toArray(new Symbol[newVParams.size()]);
                return new Type.PolyType(tparams,
                                         new Type.MethodType(newVParamsA,
                                                             result));
                }
            }
            throw Debug.abort("unexpected type: ", type);
        } else
            return type;
    }

    private boolean isPrimitive(Symbol sym) {
        return sym == defs.ANY_IS
            || sym == defs.ANY_AS;
    }

    private boolean isNestedClass(Symbol classSym) {
        return !classSym.owner().isPackageClass();
    }

    public void apply(CompilationUnit unit) {
        transformer.apply(unit);
    }

    private class TV_Transformer extends GenTransformer {
        private Symbol currentOwner;

        public TV_Transformer(Global global) {
            super(global);
        }

        public Tree transform(Tree tree) {
            try {
                return transform0(tree);
            } catch (Error e) {
                System.out.println("tree: " + tree);
                throw e;
            }
        }

        public Tree transform0(Tree tree) {
            if (tree instanceof Tree.ClassDef) {
                Tree.ClassDef classDef = (Tree.ClassDef)tree;
                Tree.Template impl = classDef.impl;
                Symbol sym = tree.symbol();

//                 if (impl.symbol().isNone())
//                     throw new Error("no symbol for " + tree);

                TreeList newBody =
                    new TreeList(transform(impl.body, impl.symbol()));
                NewMember[] toAdd = membersToAdd(sym);
                for (int i = 0; i < toAdd.length; ++i) {
                    if (toAdd[i] instanceof NewMember.TypeAccessor) {
                        NewMember.TypeAccessor member =
                            (NewMember.TypeAccessor)toAdd[i];
                        newBody.append(typeAccessorBody(member.memSym, member.accSym));
                    } else if (toAdd[i] instanceof NewMember.TypeConstructor) {
                        NewMember.TypeConstructor member =
                            (NewMember.TypeConstructor)toAdd[i];
                        newBody.append(tConstructorVal(member.memSym, member.tcSym));
                    } else if (toAdd[i] instanceof NewMember.Instantiator) {
                        NewMember.Instantiator member =
                            (NewMember.Instantiator)toAdd[i];
                        newBody.append(instantiatorBody(member.memSym, member.insSym));
                    } else if (toAdd[i] instanceof NewMember.ClassInitialiser) {
                        NewMember.ClassInitialiser member =
                            (NewMember.ClassInitialiser)toAdd[i];
                        newBody.append(classInitialiser(member.memSym,
                                                        member.ciSym,
                                                        member.tcSym));
                    } else {
                        throw Debug.abort("unexpected new member", toAdd[i]);
                    }
                }

                Symbol pConst = sym.primaryConstructor();

                return gen.ClassDef(sym,
                                    transform(impl.parents, pConst),
                                    impl.symbol(),
                                    newBody.toArray());
            } else if (tree instanceof Tree.DefDef) {
                Tree.DefDef defDef = (Tree.DefDef)tree;
                Symbol symbol = getSymbolFor(tree);
                Tree rhs = defDef.rhs;

                // TODO maybe use "overrides" method instead of name
                // to identify the "getType" method.
                if (symbol.name == Names.getType) {
                    // Correct the body of the getType method which,
                    // until now, was a placeholder (introduced by
                    // RefCheck).
                    rhs = scalaClassType(symbol.pos,
                                         symbol.owner().type(),
                                         symbol,
                                         EENV);
                }

                return gen.DefDef(symbol, transform(rhs, symbol));
            } else if (tree instanceof Tree.ValDef) {
                Tree.ValDef valDef = (Tree.ValDef)tree;
                Symbol symbol = getSymbolFor(tree);
                if (valDef.rhs instanceof Tree.Literal
                    && ((Tree.Literal)valDef.rhs).value == AConstant.ZERO) {
                    Tree defaultValue =
                        gen.mkRef(tree.pos,
                                  typeAsValue(tree.pos,
                                              valDef.tpe.type,
                                              currentOwner,
                                              EENV),
                                  defs.TYPE_DEFAULTVALUE());
                    Tree rhs = gen.mkApply__(tree.pos, defaultValue);
                    return gen.ValDef(symbol, rhs);
                }
                return gen.ValDef(symbol, transform(valDef.rhs, symbol));
            } else if (tree instanceof Tree.New) {
                Tree init = ((Tree.New)tree).init;
                if (init instanceof Tree.Apply) {
                    Tree.Apply initApply = (Tree.Apply)init;
                    if (initApply.fun instanceof Tree.TypeApply) {
                        Tree.TypeApply initTypeApply = (Tree.TypeApply)initApply.fun;
                        Tree fun = initTypeApply.fun;
                        Tree[] targs = initTypeApply.args;
                        Tree[] vargs = initApply.args;
                        if (fun.symbol() == ARRAY_CONSTRUCTOR && false) {
                            assert targs.length == 1;
                            assert vargs.length == 1;
                            Tree newArrayfun =
                                gen.mkRef(tree.pos,
                                          typeAsValue(targs[0].pos,
                                                      targs[0].type,
                                                      currentOwner,
                                                      EENV),
                                          defs.TYPE_NEWARRAY());
                            return gen.mkApplyTV(newArrayfun, targs, vargs);
                        }
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
                    assert targs.length == 1 && vargs.length == 0;
                    Type type = targs[0].type;
                    Tree expr = transform(qualifierOf(fun));
                    return isTrivialType(type)
                        ? super.transform(tree)
                        : genInstanceTest(tree.pos, expr, type);
                } else if (funSym == defs.ANY_AS) {
                    assert targs.length == 1 && vargs.length == 0;
                    Type type = targs[0].type;
                    Tree expr = transform(qualifierOf(fun));
                    return isTrivialType(type)
                        ? super.transform(tree)
                        : genTypeCast(tree.pos, expr, type);
                } else if (funSym == ARRAY_CONSTRUCTOR) {
                    return super.transform(tree);
                } else {
                    Tree[] newVArgs = transform(vargs);
                    Tree[] finalVArgs =
                        new Tree[newVArgs.length + targs.length];
                    for (int i = 0; i < targs.length; ++i)
                        finalVArgs[i] = typeAsValue(targs[i].pos,
                                                    targs[i].type,
                                                    currentOwner,
                                                    EENV);
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

        private int level(Symbol sym) {
            Symbol superClass = sym.parents()[0].symbol();
            assert superClass != Symbol.NONE : sym;
            if (superClass == defs.ANY_CLASS)
                return 0;
            else
                return 1 + level(superClass);
        }

        /**
         * Return a method giving access to the given type, as a
         * value.
         */
        private Tree.DefDef typeAccessorBody(Symbol typSym, Symbol accSym) {
            Tree rhs;
            if (typSym.isAbstractType())
                rhs = Tree.Empty;
            else if (typSym.isClass())
                rhs = scalaClassType(typSym.pos, typSym.type(), accSym, EENV);
            else {
                final Symbol[] vparams = accSym.valueParams();
                final int pos = accSym.pos;

                final HashMap varMap = new HashMap();
                Symbol[] tparams = typSym.typeParams();
                for (int i = 0; i < tparams.length; ++i)
                    varMap.put(tparams[i], new Integer(i));

                TEnv tEnv = new TEnv() {
                        public boolean definesVar(Symbol sym) {
                            return varMap.containsKey(sym);
                        }

                        public Tree treeForVar(Symbol sym) {
                            int idx = ((Integer)varMap.get(sym)).intValue();
                            Tree array = gen.mkLocalRef(pos, vparams[0]);
                            return gen.mkArrayGet(pos, array, idx);
                        }
                    };

                rhs = typeAsValue(typSym.pos, typSym.type(), accSym, tEnv);
            }
            return gen.DefDef(accSym, rhs);
        }

        private Tree tConstructorVal(Symbol clsSym, Symbol tcSym) {
            return gen.ValDef(tcSym,
                              tcSym.isStatic()
                              ? Tree.Empty
                              : tConstructorRHS(tcSym.pos, clsSym, tcSym));
        }

        private Tree classInitialiser(Symbol clsSym,
                                      Symbol ciSym,
                                      Symbol tcSym) {
            if (tcSym.isStatic()) {
                int pos = tcSym.pos;
                Tree rhs = tConstructorRHS(pos, clsSym, ciSym);
                Tree assign = gen.Assign(pos, gen.Ident(pos, tcSym), rhs);

                return gen.DefDef(ciSym, assign);
            } else
                return Tree.Empty;
        }

        private Tree tConstructorRHS(int pos, Symbol clsSym, Symbol owner) {
            int zCount = 0, mCount = 0, pCount = 0;
            Symbol[] tparams = clsSym.typeParams();

            for (int i = 0; i < tparams.length; ++i) {
                if ((tparams[i].flags & Modifiers.COVARIANT) != 0)
                    ++pCount;
                else if ((tparams[i].flags & Modifiers.CONTRAVARIANT) != 0)
                    ++mCount;
                else
                    ++zCount;
            }

            int[] ancestorCode = getAncestorCode(computeAncestors(clsSym));

            Tree outer = isNestedClass(clsSym)
                ? (clsSym.owner().isClass()
                   ? gen.This(pos, clsSym.owner())
                   : gen.New(gen.mkApply__(gen.mkPrimaryConstructorGlobalRef(pos,
                                                                             defs.OBJECT_CLASS))))
                : gen.mkNullLit(pos);

            Tree[] tcArgs = new Tree[] {
                gen.mkIntLit(pos, level(clsSym)),
                gen.mkSymbolNameLit(pos, clsSym),
                outer,
                gen.mkIntLit(pos, zCount),
                gen.mkIntLit(pos, mCount),
                gen.mkIntLit(pos, pCount),
                mkNewIntLitArray(pos, ancestorCode, owner)
            };

            Symbol tcConst = defs.TYPECONSTRUCTOR_CLASS.primaryConstructor();
            Tree tcCall =
                gen.mkApply_V(pos, gen.mkGlobalRef(pos, tcConst), tcArgs);
            return gen.New(pos, tcCall);
        }

        private Tree mkNewIntLitArray(int pos, int[] values, Symbol owner) {
            Tree[] intLits = new Tree[values.length];
            for (int i = 0; i < values.length; ++i)
                intLits[i] = gen.mkIntLit(pos, values[i]);
            return gen.mkNewArray(pos, defs.INT_TYPE(), intLits, owner);
        }

        /**
         * Return a method to instantiate the given type.
         */
        private Tree.DefDef instantiatorBody(Symbol clsSym, Symbol insSym) {
            // TODO fix flags for all symbols below
            final int pos = clsSym.pos;
            final Symbol[] vparams = insSym.valueParams();

            Tree[] body = new Tree[2];

            // Generate call to "getInstantiation" method of
            // constructor.
            Tree getInstFun =
                gen.Select(pos,
                           gen.mkLocalRef(pos, getTConstructorSym(clsSym)),
                           defs.TYPECONSTRUCTOR_GETINSTANTIATION());

            Tree[] getInstArgs = new Tree[]{ gen.mkLocalRef(pos, vparams[0]) };

            Symbol instVal =
                insSym.newVariable(pos, 0, Name.fromString("inst"));
            instVal.setInfo(defs.SCALACLASSTYPE_TYPE());

            Tree instValDef =
                gen.ValDef(instVal,
                           gen.mkApply_V(pos, getInstFun, getInstArgs));

            // Generate test to see if a call to "instantiate" is
            // necessary.
            Tree cond =
                gen.mkApply_V(pos,
                              gen.Select(pos,
                                         gen.mkLocalRef(pos, instVal),
                                         defs.ANY_BANGEQ),
                              new Tree[] { gen.mkNullLit(pos) });
            Tree thenP = gen.mkLocalRef(pos, instVal);

            final HashMap varMap = new HashMap();
            Symbol[] tparams = clsSym.typeParams();
            for (int i = 0; i < tparams.length; ++i)
                varMap.put(tparams[i], new Integer(i));

            // Type environment mapping the type parameters of the
            // class to their corresponding element in the "types"
            // array passed to this instantiator.
            TEnv tEnv = new TEnv() {
                    public boolean definesVar(Symbol sym) {
                        return varMap.containsKey(sym);
                    }

                    public Tree treeForVar(Symbol sym) {
                        int idx = ((Integer)varMap.get(sym)).intValue();
                        Tree array = gen.mkLocalRef(pos, vparams[0]);
                        return gen.mkArrayGet(pos, array, idx);
                    }
                };

            Type[] parents = clsSym.parents();
            TreeList parentTypes = new TreeList();
            for (int i = 0; i < parents.length; ++i) {
                Type parent = parents[i];
                if (!parent.symbol().isJava()) {
                    Tree parentType =
                        typeAsValue(pos, parent, insSym, tEnv);
                    parentTypes.append(parentType);
                }
            }
            boolean emptyParents = (parentTypes.length() == 0);
            Tree parentsArray = emptyParents
                ? gen.mkGlobalRef(pos, defs.SCALACLASSTYPE_EMPTYARRAY())
                : gen.mkNewArray(pos,
                                 defs.SCALACLASSTYPE_TYPE(),
                                 parentTypes.toArray(),
                                 insSym);

            Tree instFun =
                gen.Select(pos,
                           gen.mkLocalRef(pos, getTConstructorSym(clsSym)),
                           defs.TYPECONSTRUCTOR_INSTANTIATE());
            Tree[] instArgs = new Tree[] {
                gen.mkLocalRef(pos, vparams[0]),
                emptyParents ? parentsArray : gen.mkNullLit(pos)
            };
            Tree instCall = gen.mkApply_V(pos, instFun, instArgs);

            Tree elseP;
            if (!emptyParents) {
                Tree setParentsFun =
                    gen.Select(pos, instCall, defs.SCALACLASSTYPE_SETPARENTS());

                elseP = gen.mkApply_V(pos,
                                      setParentsFun,
                                      new Tree[] { parentsArray });
            } else
                elseP = instCall;

            Tree ifExpr =
                gen.If(pos, cond, thenP, elseP, defs.SCALACLASSTYPE_TYPE());

            return gen.DefDef(insSym, gen.mkBlock(pos, instValDef, ifExpr));
        }

        /**
         * Generate code to test if the given expression is an
         * instance of the given type.
         */
        private Tree genInstanceTest(int pos, Tree expr, Type tp) {
            Tree tpVal = typeAsValue(pos, tp, currentOwner, EENV);
            Tree fun = gen.Select(pos, tpVal, defs.TYPE_ISINSTANCE());
            return gen.mkApply_V(pos, fun, new Tree[] { expr });
        }

        /**
         * Generate code to cast the given value to the given type.
         */
        private Tree genTypeCast(int pos, Tree expr, Type tp) {
            Tree tpVal = typeAsValue(pos, tp, currentOwner, EENV);
            Tree fun = gen.Select(pos, tpVal, defs.TYPE_CAST());
            Tree castCall = gen.mkApply_V(pos, fun, new Tree[] { expr });
            return gen.mkAsInstanceOf(pos, castCall, tp);
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
        private Tree typeAsValue(int pos, Type tp, Symbol owner, TEnv env) {
            if (tp instanceof Type.ConstantType) {
                return typeAsValue(pos, ((Type.ConstantType)tp).base, owner, env);
            } else if (tp instanceof Type.TypeRef) {
                Type.TypeRef typeRef = (Type.TypeRef)tp;
                Type pre = typeRef.pre;
                Symbol sym = typeRef.sym;
                Type[] args = typeRef.args;
                if (env.definesVar(sym)) {
                    assert args.length == 0;
                    return env.treeForVar(sym);
                } else if (sym == defs.ARRAY_CLASS) {
                    assert args.length == 1;
                    return arrayType(pos, sym, args[0], owner, env);
                } else if (predefTypes.containsKey(sym)) {
                    return gen.mkGlobalRef(pos, (Symbol)predefTypes.get(sym));
                } else if (sym.isJava()) {
                    assert args.length <= 1
                        : Debug.show(sym) + " " + args.length;
                    return javaType(pos, sym);
                } else if (!sym.isParameter()) {
                    if (owner == null)
                        throw new Error("null owner for " + Debug.show(tp));
                    return scalaClassType(pos, tp, owner, env);
                } else {
                    assert !isValuePrefix(pre) : tp;
                    return gen.mkLocalRef(pos, getAccessorSym(sym));
                }
            } else if (tp instanceof Type.SingleType) {
                return singleType(pos, (Type.SingleType)tp);
            } else if (tp instanceof Type.ThisType) {
                return thisType(pos, ((Type.ThisType)tp).sym);
            } else if (tp instanceof Type.CompoundType) {
                Type.CompoundType compoundType = (Type.CompoundType)tp;
                return compoundType(pos,
                                    compoundType.parts,
                                    compoundType.members,
                                    owner,
                                    env);
            }
            throw Debug.abortIllegalCase(tp);
        }

        private Tree arrayType(int pos,
                               Symbol sym,
                               Type elemType,
                               Symbol owner,
                               TEnv env) {
            Tree constr =
                gen.mkPrimaryConstructorGlobalRef(pos,
                                                  defs.JAVAREFARRAYTYPE_CLASS);
            Tree[] args = new Tree[]{ typeAsValue(pos, elemType, owner, env) };
            return gen.New(pos, gen.mkApply_V(constr, args));
        }

        private Tree javaType(int pos, Symbol sym) {
            Tree constr =
                gen.mkPrimaryConstructorGlobalRef(pos,
                                                  defs.JAVACLASSTYPE_CLASS);
            Tree nameLit = gen.mkSymbolNameLit(pos, sym);
            Tree[] args = new Tree[] { nameLit };
            return gen.New(pos, gen.mkApply_V(constr, args));
        }

        private Tree thisType(int pos, Symbol sym) {
            Tree constr =
                gen.mkPrimaryConstructorGlobalRef(pos, defs.SINGLETYPE_CLASS);
            Tree[] args = new Tree[] { gen.This(pos, sym) };
            return gen.New(pos, gen.mkApply_V(constr, args));
        }

        private Tree singleType(int pos, Type.SingleType tp) {
            Tree constr =
                gen.mkPrimaryConstructorGlobalRef(pos, defs.SINGLETYPE_CLASS);
            Tree[] args = new Tree[] { gen.mkQualifier(pos, tp) };
            return gen.New(pos, gen.mkApply_V(constr, args));
        }

        private Tree compoundType(int pos,
                                  Type[] parts,
                                  Scope members,
                                  Symbol owner,
                                  TEnv env) {
            Tree[] partsT = new Tree[parts.length];
            for (int i = 0; i < parts.length; ++i)
                partsT[i] = typeAsValue(pos, parts[i], owner, env);

            Tree[] constrArgs = new Tree[] {
                gen.mkNewArray(pos, defs.CLASSTYPE_TYPE(), partsT, owner),
                gen.mkBooleanLit(pos, members.isEmpty())
            };
            Tree constr =
                gen.mkPrimaryConstructorGlobalRef(pos,
                                                  defs.COMPOUNDTYPE_CLASS);
            return gen.New(pos, gen.mkApply_V(constr, constrArgs));
        }

        private Tree scalaClassType(int pos, Type tp, Symbol owner, TEnv env) {
            if (tp instanceof Type.TypeRef) {
                Type.TypeRef typeRef = (Type.TypeRef)tp;
                Type pre = typeRef.pre;
                Symbol sym = typeRef.sym;
                Type[] args = typeRef.args;
                Symbol insSym = getInstMethSym(sym);
                Tree preFun = (isNestedClass(sym) && sym.owner().isClass())
                    ? gen.Select(pos, gen.mkQualifier(pos, pre), insSym)
                    : gen.Ident(pos, insSym);

                // TODO special case for monomorphic cases
                Tree[] insArgs;
                if (true || args.length > 0) {
                    Tree[] elems = new Tree[args.length];
                    int[] perm = typeParamsPermutation(sym.typeParams());
                    for (int i = 0; i < args.length; ++i)
                        elems[i] = typeAsValue(pos, args[perm[i]], owner, env);
                    insArgs = new Tree[] {
                        gen.mkNewArray(pos, defs.TYPE_TYPE(), elems, owner)
                    };
                } else
                    insArgs = Tree.EMPTY_ARRAY;

                return gen.mkApply_V(pos, preFun, insArgs);
            }
            throw Debug.abort("unexpected type: ", tp);
        }

        private final int VARIANT =
            Modifiers.COVARIANT | Modifiers.CONTRAVARIANT;

        /**
         * Compute the (unique) permutation which puts all invariant
         * type parameters first, followed by the contravariant ones,
         * then the covariants, preserving the relative ordering of
         * arguments with same variance.
         */
        private int[] typeParamsPermutation(Symbol[] params) {
            int[] tparamsPerm = new int[params.length];
            int permIdx = 0;

            for (int i = 0; i < params.length; ++i)
                if ((params[i].flags & VARIANT) == 0)
                    tparamsPerm[permIdx++] = i;
            for (int i = 0; i < params.length; ++i)
                if ((params[i].flags & Modifiers.CONTRAVARIANT) != 0)
                    tparamsPerm[permIdx++] = i;
            for (int i = 0; i < params.length; ++i)
                if ((params[i].flags & Modifiers.COVARIANT) != 0)
                    tparamsPerm[permIdx++] = i;
            assert permIdx == tparamsPerm.length;

            return tparamsPerm;
        }

        /**
         * Extract qualifier from a tree, which must be a Select node.
         */
        private Tree qualifierOf(Tree tree) {
            if (tree instanceof Tree.Select)
                return ((Tree.Select)tree).qualifier;
            throw Debug.abort("cannot extract qualifier from ", tree);
        }

        private boolean isValuePrefix(Type pre) {
            if (pre instanceof Type.ThisType) {
                Symbol clazz = ((Type.ThisType)pre).sym;
                return !(clazz.isPackage() || clazz.isNone());
            } else if (pre == Type.NoPrefix) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Transform a prefix into a tree representing it.
         */
        private Tree prefixAsValue(int pos, Type pre) {
            assert isValuePrefix(pre);
            if (pre instanceof Type.ThisType) {
                return gen.This(pos, ((Type.ThisType)pre).sym);
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

        private Ancestor[][] computeAncestors0(Symbol classSym) {
            Symbol[] scalaParents = scalaParents(classSym);
            int level = level(classSym);
            ArrayList/*<Ancestor>*/[] ancestor = new ArrayList[level + 1];

            for (int l = 0; l < ancestor.length; ++l)
                ancestor[l] = new ArrayList();

            ancestor[level].add(new Ancestor(classSym, -1, -1));

            for (int p = 0; p < scalaParents.length; ++p) {
                Symbol parentSymbol = scalaParents[p];
                assert parentSymbol != Symbol.NONE;

                Ancestor[][] parentAncestor = computeAncestors(parentSymbol);
                assert parentAncestor.length <= ancestor.length;

                for (int l = 0; l < parentAncestor.length; ++l) {
                    ArrayList/*<Ancestor>*/ myRow = ancestor[l];
                    Ancestor[] parentRow = parentAncestor[l];
                    for (int i = 0; i < parentRow.length; ++i) {
                        Symbol sym = parentRow[i].symbol;

                        Iterator myRowIt = myRow.iterator();
                        boolean alreadyExists = false;
                        while (!alreadyExists && myRowIt.hasNext()) {
                            Ancestor myAncestor = (Ancestor)myRowIt.next();
                            if (myAncestor.symbol == sym)
                                alreadyExists = true;
                        }

                        if (!alreadyExists)
                            myRow.add(new Ancestor(sym, p, i));
                    }
                }
            }

            Ancestor[][] finalAncestor = new Ancestor[level + 1][];
            for (int i = 0; i < finalAncestor.length; ++i) {
                finalAncestor[i] = (Ancestor[])
                    ancestor[i].toArray(new Ancestor[ancestor[i].size()]);
            }

            return finalAncestor;
        }

        private Ancestor[][] computeAncestors(Symbol classSym) {
            Ancestor[][] ancestor = (Ancestor[][])ancestorCache.get(classSym);
            if (ancestor == null) {
                ancestor = computeAncestors0(classSym);
                ancestorCache.put(classSym, ancestor);
            }
            return ancestor;
        }

        private Symbol[] scalaParents(Symbol classSym) {
            Type[] parentTypes = classSym.parents();
            ArrayList scalaParents = new ArrayList();
            for (int i = 0; i < parentTypes.length; ++i) {
                Symbol parentSym = parentTypes[i].symbol();
                if (!parentSym.isJava())
                    scalaParents.add(parentSym);
            }
            return (Symbol[])
                scalaParents.toArray(new Symbol[scalaParents.size()]);
        }

        private int[] getAncestorCode(Ancestor[][] ancestor) {
            ArrayList/*<List<Ancestor>>*/ prunedRows = new ArrayList();

            int totalSize = 0;
            for (int l = 0; l < ancestor.length; ++l) {
                Ancestor[] row = ancestor[l];
                ArrayList/*<Ancestor>*/ prunedRow = new ArrayList(row.length);
                for (int i = 0; i < row.length; ++i) {
                    if (row[i].parentIndex > 0)
                        prunedRow.add(row[i]);
                }

                prunedRows.add(prunedRow);
                totalSize += 1 + 2 * prunedRow.size();
            }

            int[] res = new int[totalSize];
            int i = 0;
            Iterator rowsIt = prunedRows.iterator();
            while (rowsIt.hasNext()) {
                ArrayList row = (ArrayList)rowsIt.next();
                res[i++] = row.size();
                Iterator ancIt = row.iterator();
                while (ancIt.hasNext()) {
                    Ancestor anc = (Ancestor)ancIt.next();
                    res[i++] = anc.parentIndex;
                    res[i++] = anc.position;
                }
            }
            assert i == totalSize;
            return res;
        }
    }

    /*Pair<ArrayList<Refinement>, HashMap<Refinement,Origin>>*/
    private Pair refinements(Type[] parents, Scope members) {
        HashMap attribution = new HashMap();

        ArrayList/*<Refinement>*/ ref = refinements(members);
        attributeRefinements(attribution, ref, -1);

        for (int i = parents.length; i > 0; --i) {
            ArrayList parentRef =
                (ArrayList)(refinements(parents[i - 1]).fst);
            attributeRefinements(attribution, parentRef, i);
            ref = mergeRefinements(parentRef, ref);
        }

        return new Pair(ref, attribution);
    }

    /*Pair<ArrayList<Refinement>, HashMap<Refinement,Origin>>*/
    private Pair refinements(Type tp) {
        return refinements(tp.parents(), tp.members());
    }

    /** Return the refinements introduced by the given members */
    private ArrayList/*<Refinement>*/ refinements(Scope members) {
        ArrayList/*<Refinement>*/ myRefs = new ArrayList();
        Scope.SymbolIterator membersIt = members.iterator();
        while (membersIt.hasNext()) {
            Symbol mem = membersIt.next();
            Symbol origMem = originalSymbol(mem);
            if (origMem != Symbol.NONE && !mem.info().isSameAs(origMem.info()))
                myRefs.add(new Refinement(origMem, mem.info()));
        }
        Collections.sort(myRefs);
        return myRefs;
    }

    private ArrayList mergeRefinements(ArrayList rs1, ArrayList rs2) {
        ArrayList/*<Refinement>*/ res =
            new ArrayList(rs1.size() + rs2.size());

        int i1 = 0, i2 = 0;
        while (i1 < rs1.size() && i2 < rs2.size()) {
            Refinement r1 = (Refinement)rs1.get(i1);
            Refinement r2 = (Refinement)rs2.get(i2);
            int cmp = r1.compareTo(r2);
            if (cmp < 0) {
                res.add(r1);
                ++i1;
            } else if (cmp > 0) {
                res.add(r2);
                ++i2;
            } else {
                assert r2.type.isSubType(r1.type) : r2 + " !<: " + r1;
                res.add(r2);
                ++i1;
                ++i2;
            }
        }
        while (i1 < rs1.size())
            res.add(rs1.get(i1++));
        while (i2 < rs2.size())
            res.add(rs2.get(i2++));

        return res;
    }

    private void attributeRefinements(HashMap attribution,
                                      ArrayList refs,
                                      int parentIndex) {
        for (int i = 0; i < refs.size(); ++i)
            attribution.put(refs.get(i), new Origin(parentIndex, i));
    }

    private int[] refinementCode(ArrayList refinement, HashMap attribution) {
        int[] code = new int[1 + 2 * refinement.size()];
        int pc = 0;

        code[pc++] = refinement.size();
        Iterator refIt = refinement.iterator();
        while (refIt.hasNext()) {
            Origin orig = (Origin)attribution.get(refIt.next());
            code[pc++] = orig.parentIndex;
            code[pc++] = orig.position;
        }
        assert pc == code.length;

        return code;
    }

//     private Symbol originalSymbol(Symbol sym) {
//         Symbol orig = originalSymbol0(sym);
//         if (orig != Symbol.NONE)
//         System.out.println("orig for " + Debug.show(sym) + ": " + Debug.show(orig));
//         return orig;
//     }

    private Symbol originalSymbol(Symbol sym) {
        Type[] closure = sym.owner().closure();
        for (int i = closure.length; i > 0; --i) {
            Type parent = closure[i - 1];
            Symbol maybeOrig = sym.overriddenSymbol(parent, false);
            if (maybeOrig != Symbol.NONE)
                return maybeOrig;
        }
        return Symbol.NONE;
    }

    //////////////////////////////////////////////////////////////////////

    private static class TEnv {
        public boolean definesVar(Symbol sym) {
            return false;
        }
        public Tree treeForVar(Symbol sym) {
            throw Debug.abort("no tree for variable " + sym);
        }
    }

    private static abstract class NewMember {
        public abstract Symbol symbolToAdd();

        public static final class TypeAccessor extends NewMember {
            public final Symbol memSym;
            public final Symbol accSym;

            public TypeAccessor(Symbol memSym, Symbol accSym) {
                this.memSym = memSym;
                this.accSym = accSym;
            }

            public Symbol symbolToAdd() {
                return accSym;
            }
        }

        public static final class TypeConstructor extends NewMember {
            public final Symbol memSym;
            public final Symbol tcSym;

            public TypeConstructor(Symbol memSym, Symbol tcSym) {
                this.memSym = memSym;
                this.tcSym = tcSym;
            }

            public Symbol symbolToAdd() {
                return tcSym;
            }
        }

        public static final class Instantiator extends NewMember {
            public final Symbol memSym;
            public final Symbol insSym;

            public Instantiator(Symbol memSym, Symbol insSym) {
                this.memSym = memSym;
                this.insSym = insSym;
            }

            public Symbol symbolToAdd() {
                return insSym;
            }
        }

        public static final class ClassInitialiser extends NewMember {
            public final Symbol memSym;
            public final Symbol ciSym;
            public final Symbol tcSym;

            public ClassInitialiser(Symbol memSym, Symbol ciSym, Symbol tcSym) {
                this.memSym = memSym;
                this.ciSym = ciSym;
                this.tcSym = tcSym;
            }

            public Symbol symbolToAdd() {
                return ciSym;
            }
        }
    }

    private static class Ancestor {
        public final Symbol symbol;
        public final int parentIndex;
        public final int position;

        public Ancestor(Symbol symbol, int parentIndex, int position) {
            this.symbol = symbol;
            this.parentIndex = parentIndex;
            this.position = position;
        }
    }

    private static class Origin {
        public final int parentIndex;
        public final int position;
        public Origin(int parentIndex, int position) {
            this.parentIndex = parentIndex;
            this.position = position;
        }
    }

    private static class Pair {
        public final Object fst, snd;
        public Pair(Object fst, Object snd) {
            this.fst = fst;
            this.snd = snd;
        }
    }

    private class Refinement implements Comparable {
        public final Symbol member;
        public final int hash;
        public final Type type;

        private final SymbolNameWriter symWriter = new SymbolNameWriter();

        public Refinement(Symbol member, Type type) {
            this.member = member;
            this.hash = memberHash(member);
            this.type = type;
        }

        public boolean equals(Object thatO) {
            return (thatO instanceof Refinement) && (compareTo(thatO) == 0);
        }

        public int compareTo(Object thatO) {
            Refinement that = (Refinement)thatO;
            if (this.hash < that.hash)
                return -1;
            else if (this.hash > that.hash)
                return 1;
            else
                return 0;
        }

        public String toString() {
            return "<" + hash + " " + member + " : " + type + ">";
        }

        private int memberHash(Symbol sym) {
            // TODO use FNV hash
            return symWriter.toString(sym).hashCode();
        }
    }
}
