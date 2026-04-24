/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Erasure.java,v 1.48 2003/01/16 14:21:19 schinz Exp $
// $Id$

package scalac.transformer;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import scalac.Global;
import scalac.Phase;
import scalac.Unit;
import scalac.ast.Tree;
import scalac.ast.Tree.Ident;
import scalac.ast.Tree.Template;
import scalac.ast.Tree.AbsTypeDef;
import scalac.ast.Tree.AliasTypeDef;
import scalac.ast.Tree.ValDef;
import scalac.ast.TreeList;
import scalac.ast.GenTransformer;
import scalac.atree.AConstant;
import scalac.symtab.Definitions;
import scalac.symtab.Kinds;
import scalac.symtab.Type;
import scalac.symtab.TypeTags;
import scalac.symtab.Modifiers;
import scalac.symtab.Scope;
import scalac.symtab.Scope.SymbolIterator;
import scalac.symtab.SymSet;
import scalac.symtab.Symbol;
import scalac.symtab.SymbolTablePrinter;
import scalac.backend.Primitive;
import scalac.backend.Primitives;
import scalac.util.Name;
import scalac.util.Names;
import scalac.util.Debug;

/** A transformer for type erasure and bridge building
 *
 *  @author     Martin Odersky
 *  @version    1.0
 *
 *  What it does:
 *  (1) Map every type to its erasure.
 *  (2) If method A overrides a method B, and the erased type ETA of A is
 *      different from the erased type ETB of B seen as a member of A's class,
 *      add a bridge method with the same name as A,B, with signature ETB
 *      which calls A after casting parameters.
 */
public class Erasure extends GenTransformer implements Modifiers {

    //########################################################################
    // Private Constants

    /** The unboxed types */
    private static final Type
        UNBOXED_UNIT    = Type.unboxedType(TypeTags.UNIT),
        UNBOXED_BOOLEAN = Type.unboxedType(TypeTags.BOOLEAN),
        UNBOXED_BYTE    = Type.unboxedType(TypeTags.BYTE),
        UNBOXED_SHORT   = Type.unboxedType(TypeTags.SHORT),
        UNBOXED_CHAR    = Type.unboxedType(TypeTags.CHAR),
        UNBOXED_INT     = Type.unboxedType(TypeTags.INT),
        UNBOXED_LONG    = Type.unboxedType(TypeTags.LONG),
        UNBOXED_FLOAT   = Type.unboxedType(TypeTags.FLOAT),
        UNBOXED_DOUBLE  = Type.unboxedType(TypeTags.DOUBLE);

    //########################################################################
    // Private Fields

    /** The global definitions */
    private final Definitions definitions;

    /** The global primitives */
    private final Primitives primitives;

    /** The current unit */
    private Unit unit;

    //########################################################################
    // Public Constructors

    /** Initializes this instance. */
	public Erasure(Global global) {
        super(global);
	this.definitions = global.definitions;
        this.primitives = global.primitives;
    }

    //########################################################################
    // Public Methods

    /** Transforms the given unit. */
    public void apply(Unit unit) {
	this.unit = unit;
	super.apply(unit);
    }

    /** Transforms the given tree. */
    public Tree transform(Tree tree) {
        if (tree instanceof Tree.ClassDef) {
            Tree.ClassDef classDef = (Tree.ClassDef)tree;
            Tree[] body = classDef.impl.body;
            Symbol clasz = tree.symbol();
            TreeList members = new TreeList(transform(body));
            checkOverloadedTermsOf(clasz);
            addBridges(clasz, members);
            return gen.ClassDef(clasz, members.toArray());
        } else if (tree instanceof Tree.ValDef) {
            Tree rhs = ((Tree.ValDef)tree).rhs;
            Symbol field = tree.symbol();
	    if (rhs != Tree.Empty) rhs = transform(rhs, field.nextType());
	    return gen.ValDef(field, rhs);
        } else if (tree instanceof Tree.DefDef) {
            Tree rhs = ((Tree.DefDef)tree).rhs;
            Symbol method = tree.symbol();
            if (rhs != Tree.Empty)
                rhs = transform(rhs, method.nextType().resultType());
	    return gen.DefDef(method, rhs);
        } else if (tree instanceof Tree.LabelDef) {
            Tree.LabelDef labelDef = (Tree.LabelDef)tree;
            Ident[] params = labelDef.params;
            Tree body = labelDef.rhs;
            Symbol label = tree.symbol();
            body = transform(body, label.nextType().resultType());
	    return gen.LabelDef(label, Tree.symbolOf(params), body);
        } else if (tree instanceof Tree.Assign) {
            Tree.Assign assign = (Tree.Assign)tree;
            Tree lhs = assign.lhs;
            Tree rhs = assign.rhs;
	    lhs = transform(lhs);
	    rhs = transform(rhs, lhs.type);
	    return gen.Assign(tree.pos, lhs, rhs);
        } else if (tree instanceof Tree.Return) {
            Tree expr = ((Tree.Return)tree).expr;
            Symbol method = tree.symbol();
            Type type = method.nextType().resultType();
	    return gen.Return(tree.pos, method, transform(expr, type));
        } else if (tree instanceof Tree.New) {
            Tree init = ((Tree.New)tree).init;
            if (tree.getType().symbol() == definitions.ARRAY_CLASS) {
                if (init instanceof Tree.Apply) {
                    Tree[] args = ((Tree.Apply)init).args;
                    assert args.length == 1: tree;
                    Type element = getArrayElementType(tree.getType()).erasure();
                    Tree size = transform(args[0]);
                    return genNewUnboxedArray(tree.pos, element, size);
                }
                throw Debug.abort("illegal case", tree);
            }
	    return gen.New(tree.pos, transform(init));
        } else if (tree instanceof Tree.Create) {
            return gen.Create(tree.pos, Tree.Empty, tree.symbol());
        } else if (tree instanceof Tree.Apply
                   && ((Tree.Apply)tree).fun instanceof Tree.TypeApply) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree.TypeApply typeApply = (Tree.TypeApply)apply.fun;
            Tree fun = transform(typeApply.fun);
            Tree[] targs = typeApply.args;
            Tree[] vargs = transform(apply.args);
            Symbol symbol = fun.symbol();
            if (symbol == definitions.ANY_AS) {
                assert targs.length == 1 && vargs.length == 0: tree;
                return coerce(getQualifier(fun), targs[0].getType().erasure());
            }
            if (symbol == definitions.ANY_IS) {
                assert targs.length == 1 && vargs.length == 0: tree;
                Type type = targs[0].type.erasure();
                if (isUnboxedSimpleType(type)) type = targs[0].type;
                return gen.mkIsInstanceOf(tree.pos, getQualifier(fun), type);
            }
            return genApply(tree.pos, fun, vargs);
        } else if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            Tree fun = transform(apply.fun);
            Tree[] vargs = transform(apply.args);
            if (fun instanceof Tree.Select
                && ((Tree.Select)fun).qualifier instanceof Tree.Apply) {
                Tree.Apply boxedApply = (Tree.Apply)((Tree.Select)fun).qualifier;
                Tree bfun = boxedApply.fun;
                Tree[] bargs = boxedApply.args;
                Symbol bsym = bfun.symbol();
                if (primitives.getPrimitive(bsym) == Primitive.BOX) {
                    assert bargs.length == 1: fun;
                    switch (primitives.getPrimitive(fun.symbol())) {
                    case COERCE: {
                        assert vargs.length == 0: tree;
                        Tree value = bargs[0];
                        return coerce(value, fun.type().resultType());
                    }
                    case LENGTH: {
                        assert vargs.length == 0: tree;
                        Tree array = bargs[0];
                        return genUnboxedArrayLength(tree.pos, array);
                    }
                    case APPLY: {
                        assert vargs.length == 1: tree;
                        Tree array = bargs[0];
                        Tree index = vargs[0];
                        return genUnboxedArrayGet(tree.pos, array, index);
                    }
                    case UPDATE: {
                        assert vargs.length == 2: tree;
                        Tree array = bargs[0];
                        Tree index = vargs[0];
                        Tree value = vargs[1];
                        return genUnboxedArraySet(tree.pos, array, index, value);
                    }
                    }
                }
            }
            return genApply(tree.pos, fun, vargs);
        } else if (tree instanceof Tree.Select) {
            Tree qualifier = ((Tree.Select)tree).qualifier;
            Symbol symbol = tree.symbol();
            Type prefix = qualifier.type().baseType(symbol.owner()).erasure();
            assert prefix != Type.NoType: tree + " -- " + Debug.show(symbol);
	    qualifier = transform(qualifier);
	    qualifier = coerce(qualifier, prefix);

            // Might end up with "box(unbox(...))". That's needed by backend.
            if (isUnboxedType(prefix)) qualifier = box(qualifier, true);
	    return gen.Select(tree.pos, qualifier, symbol);
        } else if (tree instanceof Tree.Literal
                   && ((Tree.Literal)tree).value == AConstant.ZERO) {
	    return gen.mkNullLit(tree.pos);
        } else if (tree instanceof Tree.Block
                   || tree instanceof Tree.If
                   || tree instanceof Tree.Switch) {
            return transform(tree, tree.getType().fullErasure());
        }
        return super.transform(tree);
    }

    //########################################################################
    // Private Methods - Tree transformation

    /** Transforms the given trees with given prototype. */
    private Tree[] transform(Tree[] trees, Type pt) {
        for (int i = 0; i < trees.length; i++) {
            Tree tree = transform(trees[i], pt);
            if (tree == trees[i]) continue;
            Tree[] array = new Tree[trees.length];
            for (int j = 0; j < i ; j++) array[j] = trees[j];
            array[i] = tree;
            while (++i < trees.length) array[i] = transform(trees[i], pt);
            return array;
        }
        return trees;
    }

    /** Transforms the given tree with given prototype. */
    private Tree transform(Tree tree, Type pt) {
        if (tree == Tree.Empty) {
            return transform(gen.mkDefaultValue(tree.pos, pt), pt);
        } else if (tree instanceof Tree.Block) {
            Tree.Block block = (Tree.Block)tree;
            return gen.Block(tree.pos, transform(block.stats), transform(block.expr, pt));
        } else if (tree instanceof Tree.If) {
            Tree.If ifTree = (Tree.If)tree;
            Tree cond = ifTree.cond;
            Tree thenp = ifTree.thenp;
            Tree elsep = ifTree.elsep;
	    cond = transform(cond, UNBOXED_BOOLEAN);
	    thenp = transform(thenp, pt);
	    elsep = transform(elsep, pt);
	    return gen.If(tree.pos, cond, thenp, elsep, pt);
        } else if (tree instanceof Tree.Switch) {
            Tree.Switch switchTree = (Tree.Switch)tree;
            Tree test = switchTree.test;
            int[] tags = switchTree.tags;
            Tree[] bodies = switchTree.bodies;
            Tree otherwise = switchTree.otherwise;
	    test = transform(test, UNBOXED_INT);
            bodies = transform(bodies, pt);
            otherwise = transform(otherwise, pt);
            return gen.Switch(tree.pos, test, tags, bodies, otherwise, pt);
        } else if (tree instanceof Tree.Return) {
            // Preserve return side effects while synthesizing the value flow.
            Tree value = transform(gen.mkDefaultValue(tree.pos, pt), pt);
            return gen.mkBlock(transform(tree), value);
        } else if (tree instanceof Tree.LabelDef
                   || tree instanceof Tree.Assign
                   || tree instanceof Tree.New
                   || tree instanceof Tree.Apply
                   || tree instanceof Tree.Super
                   || tree instanceof Tree.This
                   || tree instanceof Tree.Select
                   || tree instanceof Tree.Ident
                   || tree instanceof Tree.Literal) {
            return coerce(transform(tree), pt);
        }
        throw Debug.abort("illegal case", tree);
    }

    /** Transforms Unit literal with given prototype. */
    private Tree transformUnit(int pos, Type pt) {
        Tree unit = pt.isSameAs(UNBOXED_UNIT)
            ? gen.mkUnitLit(pos)
            : gen.mkApply__(gen.mkGlobalRef(pos, primitives.BOX_UVALUE));
        return coerce(unit, pt);
    }

    /** Coerces the given tree to the given type. */
    private Tree coerce(Tree tree, Type pt) {
        if (pt.isSameAs(UNBOXED_UNIT)) {
            if (tree.type() == Type.ErrorType) return tree;
            if (tree.type().isSameAs(UNBOXED_UNIT)
                || isSubType(tree.type(), definitions.UNIT_TYPE())) {
                return tree.type().isSameAs(UNBOXED_UNIT) ? tree : unbox(tree, pt);
            }
            return gen.mkUnitBlock(tree);
        }
        if (isSubType(tree.type(), pt)) {
            if (tree.type().symbol() == definitions.ARRAY_CLASS) {
                if (pt.symbol() != definitions.ARRAY_CLASS) {
                    Symbol symbol = primitives.UNBOX__ARRAY;
                    Tree unboxtree = gen.mkGlobalRef(tree.pos, symbol);
                    tree = gen.mkApply_V(unboxtree, new Tree[] {tree});
                    return coerce(tree, pt);
                }
            }
            return tree;
        }
        if (isUnboxedSimpleType(tree.type())) {
            if (isUnboxedSimpleType(pt)) return convert(tree, pt);
            Type to = pt.erasure();
            if (!isUnboxedSimpleType(to) || isSameAs(to, tree.type()))
                return box(tree);
            else
                return coerce(convert(tree, to), pt);
        } else if (isUnboxedArrayType(tree.type())) {
            if (!isUnboxedArrayType(pt)) return coerce(box(tree), pt);
        } else if (isUnboxedSimpleType(pt)) {
            Type from = tree.type().erasure();
            if (isUnboxedSimpleType(from))
                return convert(unbox(tree, from), pt);
            else
                return unbox(coerce(tree, boxUnboxedType(pt)), pt);
        } else if (isUnboxedArrayType(pt)) {
            if (tree.type.symbol() == definitions.ARRAY_CLASS)
                return unbox(tree, pt);
        } else if (pt.symbol() == definitions.ARRAY_CLASS) {
            Tree boxtree = gen.mkGlobalRef(tree.pos, primitives.BOX__ARRAY);
            return gen.mkApply_V(boxtree, new Tree[]{tree});
        }
        return gen.mkAsInstanceOf(tree, pt);
    }

    /** Boxes the given tree. */
    private Tree box(Tree tree) {
        return box(tree, false);
    }

    /** Boxes the given tree. */
    private Tree box(Tree tree, boolean force) {
        if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            if (primitives.getPrimitive(apply.fun.symbol()) == Primitive.UNBOX) {
                assert apply.args.length == 1: tree;
                if (!force) return apply.args[0];
            }
        }
        Symbol symbol = primitives.getBoxValueSymbol(tree.getType());
	Tree boxtree = gen.mkGlobalRef(tree.pos, symbol);
        return tree.getType().equals(UNBOXED_UNIT)
            ? gen.mkBlock(tree, gen.mkApply__(boxtree))
            : gen.mkApply_V(boxtree, new Tree[]{tree});
    }

    /** Unboxes the given tree to the given type. */
    private Tree unbox(Tree tree, Type pt) {
        if (tree instanceof Tree.Apply) {
            Tree.Apply apply = (Tree.Apply)tree;
            if (primitives.getPrimitive(apply.fun.symbol()) == Primitive.BOX) {
                assert apply.args.length == 1: tree;
                return apply.args[0];
            }
        }
        Symbol symbol = primitives.getUnboxValueSymbol(pt);
        return gen.mkApply_V(
            gen.mkGlobalRef(tree.pos, symbol), new Tree[]{tree});
    }

    /** Converts the given tree to the given type. */
    private Tree convert(Tree tree, Type to) {
        Symbol symbol = primitives.getConvertSymbol(tree.type(), to);
        return gen.mkApply_V(
            gen.mkGlobalRef(tree.pos, symbol), new Tree[]{tree});
    }

    //########################################################################
    // Private Methods - Tree generation

    /** Generates given bridge method forwarding to given method. */
    private Tree genBridgeMethod(Symbol bridge, Symbol method) {
        Type type = bridge.nextType();
        Tree body = genApply(bridge.pos,
            gen.Select(gen.This(bridge.pos, bridge.owner()), method),
            gen.mkLocalRefs(bridge.pos, type.valueParams()));
        return gen.DefDef(bridge, coerce(body, type.resultType()));
    }

    /** Generates an application with given function and arguments. */
    private Tree genApply(int pos, Tree fun, Tree[] args) {
        if (fun.getType() instanceof Type.MethodType) {
            Symbol[] params = ((Type.MethodType)fun.getType()).vparams;
            Tree[] args1 = args;
            for (int i = 0; i < args.length; i++) {
                Tree arg = args[i];
                Tree arg1 = coerce(arg, params[i].nextType());
                if (arg1 != arg && args1 == args) {
                    args1 = new Tree[args.length];
                    for (int j = 0; j < i; j++) args1[j] = args[j];
                }
                args1[i] = arg1;
            }
            return gen.mkApply_V(pos, fun, args1);
        }
        throw Debug.abort("illegal type " + fun.getType() + " for " + fun);
    }

    /**
     * Generates a new unboxed array of given size and with elements
     * of given type.
     */
    private Tree genNewUnboxedArray(int pos, Type element, Tree size) {
        if (element instanceof Type.UnboxedType) {
            return genNewUnboxedArray(pos, ((Type.UnboxedType)element).tag, size);
        }
        if (global.target == global.TARGET_INT) {
            Tree[] targs = {gen.mkType(pos, element)};
            Tree[] vargs = {coerce(size, UNBOXED_INT)};
            Tree fun = gen.mkGlobalRef(pos, primitives.NEW_OARRAY);
            return gen.mkApplyTV(fun, targs, vargs);
        }
        String name = primitives.getNameForClassForName(element);
        Tree[] args = { coerce(size, UNBOXED_INT), gen.mkStringLit(pos,name) };
        Tree array =
            gen.mkApply_V(gen.mkGlobalRef(pos, primitives.NEW_OARRAY), args);
        return gen.mkAsInstanceOf(array, Type.UnboxedArrayType(element));
    }

    /**
     * Generates a new unboxed array of given size and with elements
     * of given unboxed type kind.
     */
    private Tree genNewUnboxedArray(int pos, int kind, Tree size) {
        Symbol symbol = primitives.getNewArraySymbol(kind);
        Tree[] args = { coerce(size, UNBOXED_INT) };
        return gen.mkApply_V(gen.mkGlobalRef(pos, symbol), args);
    }

    /** Generates an unboxed array length operation. */
    private Tree genUnboxedArrayLength(int pos, Tree array) {
        assert isUnboxedArrayType(array.getType()): array;
        Symbol symbol = primitives.getArrayLengthSymbol(array.getType());
        Tree[] args = { array };
        return gen.mkApply_V(gen.mkGlobalRef(pos, symbol), args);
    }

    /** Generates an unboxed array get operation. */
    private Tree genUnboxedArrayGet(int pos, Tree array, Tree index) {
        assert isUnboxedArrayType(array.getType()): array;
        Symbol symbol = primitives.getArrayGetSymbol(array.getType());
        index = coerce(index, UNBOXED_INT);
        Tree[] args = { array, index };
        return gen.mkApply_V(gen.mkGlobalRef(pos, symbol), args);
    }

    /** Generates an unboxed array set operation. */
    private Tree genUnboxedArraySet(int pos, Tree array,Tree index,Tree value){
        assert isUnboxedArrayType(array.getType()): array;
        Symbol symbol = primitives.getArraySetSymbol(array.getType());
        index = coerce(index, UNBOXED_INT);
        value = coerce(value, getArrayElementType(array.getType()));
        Tree[] args = { array, index, value };
        return gen.mkApply_V(gen.mkGlobalRef(pos, symbol), args);
    }

    //########################################################################
    // Private Methods - Queries

    /** Returns the qualifier of the given tree. */
    private Tree getQualifier(Tree tree) {
        if (tree instanceof Tree.Select) {
            return ((Tree.Select)tree).qualifier;
        }
        throw Debug.abort("no qualifier for tree", tree);
    }

    /** Are the given erased types in a subtyping relation? */
    private boolean isSubType(Type tp1, Type tp2) {
        global.nextPhase();
        boolean result = tp1.isSubType(tp2);
        global.prevPhase();
        return result;
    }

    /** Are the given erased types in an equality relation? */
    private boolean isSameAs(Type tp1, Type tp2) {
        global.nextPhase();
        boolean result = tp1.isSameAs(tp2);
        global.prevPhase();
        return result;
    }

    /** Is the given type an unboxed type? */
    private boolean isUnboxedType(Type type) {
	return type instanceof Type.UnboxedType || type instanceof Type.UnboxedArrayType;
    }

    /** Is the given type an unboxed simple type? */
    private boolean isUnboxedSimpleType(Type type) {
	return type instanceof Type.UnboxedType;
    }

    /** Is the given type an unboxed array type? */
    private boolean isUnboxedArrayType(Type type) {
	return type instanceof Type.UnboxedArrayType;
    }

    /** Returns the boxed version of the given unboxed type. */
    private Type boxUnboxedType(Type type) {
	if (type instanceof Type.UnboxedType) {
            switch (((Type.UnboxedType)type).tag) {
            case TypeTags.UNIT:
                return definitions.UNIT_CLASS.type();
            case TypeTags.BOOLEAN:
                return definitions.BOOLEAN_CLASS.type();
            case TypeTags.BYTE:
                return definitions.BYTE_CLASS.type();
            case TypeTags.SHORT:
                return definitions.SHORT_CLASS.type();
            case TypeTags.CHAR:
                return definitions.CHAR_CLASS.type();
            case TypeTags.INT:
                return definitions.INT_CLASS.type();
            case TypeTags.LONG:
                return definitions.LONG_CLASS.type();
            case TypeTags.FLOAT:
                return definitions.FLOAT_CLASS.type();
            case TypeTags.DOUBLE:
                return definitions.DOUBLE_CLASS.type();
            default:
                break;
            }
	} else if (type instanceof Type.UnboxedArrayType) {
            Type element = ((Type.UnboxedArrayType)type).elemtp;
            return Type.appliedType(
                definitions.ARRAY_CLASS.type(), new Type[] {element});
	}
        throw Debug.abort("illegal case", type);
    }

    /** Returns the element type of the given array type. */
    private Type getArrayElementType(Type type) {
        if (type instanceof Type.TypeRef) {
            Type.TypeRef typeRef = (Type.TypeRef)type;
            if (typeRef.sym == definitions.ARRAY_CLASS) {
                assert typeRef.args.length == 1: type;
                return typeRef.args[0];
            }
        } else if (type instanceof Type.UnboxedArrayType) {
            return ((Type.UnboxedArrayType)type).elemtp;
        }
        throw Debug.abort("non-array type", type);
    }

    //########################################################################
    // Private Methods - Overlapping signatures detection

    /**
     * Checks that overloaded terms of the given class have no
     * overlapping erased signatures.
     */
    private void checkOverloadedTermsOf(Symbol clasz) {
        for (SymbolIterator si = clasz.members().iterator(); si.hasNext(); ) {
            Symbol symbol = si.next();
            if (!symbol.isTerm()) continue;
            if (symbol.info() instanceof Type.OverloadedType) {
                Symbol[] symbols = ((Type.OverloadedType)symbol.info()).alts;
                Type[] types = new Type[symbols.length];
                for (int i = 0; i < symbols.length; i++) {
                    types[i] = symbols[i].nextType();
                    for (int j = 0; j < i; j++) {
                        if (!isSameAs(types[i], types[j])) continue;
                        errorOverlappingSignatures(symbols[j], symbols[i]);
                        break;
                    }
                }
            }
        }
    }

    /** Reports an overlapping signature error for given symbols. */
    private void errorOverlappingSignatures(Symbol symbol1, Symbol symbol2) {
        SymbolTablePrinter printer = new SymbolTablePrinter(" ");
        printer.print("overlapping overloaded alternatives;").space();
        printer.print("the two following alternatives of").space();
        printer.printSymbolKindAndName(symbol1).space();
        printer.print("have the same erasure:").space();
        printer.printType(symbol1.nextType());
        Phase phase = global.currentPhase;
        global.currentPhase = global.PHASE.ANALYZER.phase();
        printer.indent();
        printer.line().print("alternative 1:").space().printSignature(symbol1);
        printer.line().print("alternative 2:").space().printSignature(symbol2);
        printer.undent();
        global.currentPhase = phase;
        unit.error(symbol2.pos, printer.toString());
    }

    //########################################################################
    //########################################################################
    //########################################################################
    //########################################################################

//////////////////////////////////////////////////////////////////////////////////
// Bridge Building
/////////////////////////////////////////////////////////////////////////////////

    private TreeList bridges;
    private HashMap bridgeSyms;
    private HashMap currentMethodSyms;

    private boolean hasBridgeSymbol(Name name, Type bridgeType) {
        for (Iterator i = bridgeSyms.values().iterator(); i.hasNext(); ) {
            SymSet syms = (SymSet)i.next();
            Symbol[] brs = syms.toArray();
            for (int j = 0; j < brs.length; j++) {
                if (brs[j].name == name && isSameAs(brs[j].type(), bridgeType))
                    return true;
            }
        }
        return false;
    }

    /** Add bridge which Java-overrides `sym1' and which forwards to `sym'
     */
    public void addBridge(Symbol owner, Symbol sym, Symbol sym1) {
	Type bridgeType = sym1.nextType();
	// create bridge symbol and add to bridgeSyms(sym)
	// or return if bridge with required type already exists for sym.
	SymSet bridgesOfSym = (SymSet) bridgeSyms.get(sym);
	if (bridgesOfSym == null) bridgesOfSym = SymSet.EMPTY;
	Symbol[] brs = bridgesOfSym.toArray();
	for (int i = 0; i < brs.length; i++) {
	    if (isSameAs(brs[i].type(), bridgeType)) return;
	}
        if (hasBridgeSymbol(sym.name, bridgeType))
            return;
	Symbol bridgeSym = sym.cloneSymbol();
	bridgeSym.flags |= (SYNTHETIC | BRIDGE);
	bridgeSym.flags &= ~(JAVA | DEFERRED);
	bridgesOfSym = bridgesOfSym.incl(bridgeSym);
	bridgeSyms.put(sym, bridgesOfSym);
        bridgeSym.setOwner(owner);

	// Avoid generating a bridge that collides with an existing method
	// after erasure. That would yield an invalid classfile.
        Type erasedBridgeType = bridgeType.fullErasure();
        Symbol[] alts = declaredAlternativesOf(owner, sym.name);
        for (int i = 0; i < alts.length; i++) {
            Type altType = bridgeTypeFor(owner, alts[i]);
            if (altType != Type.NoType &&
                altType != Type.ErrorType &&
                hasSameJvmType(bridgeType, altType))
                return;
        }
	for (int i = 0; i < alts.length; i++) {
	    Type altType = bridgeTypeFor(owner, alts[i]);
	    if (sym != alts[i] &&
                altType != Type.NoType &&
                altType != Type.ErrorType &&
                hasSameJvmType(bridgeType, altType)) {
		unit.error(sym.pos, "overlapping overloaded alternatives; " +
			   "overridden " + sym1 + sym1.locationString() +
			   " has same erasure as " + alts[i] +
			   altType + alts[i].locationString());
		}
	}

	if (bridgeType instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)bridgeType;
            Symbol[] params = methodType.vparams;
            Type restp = methodType.result;
	    // assign to bridge symbol its bridge type
	    // where owner of all parameters is bridge symbol itself.
	    Symbol[] params1 = new Symbol[params.length];
	    for (int i = 0; i < params.length; i++) {
		params1[i] = params[i].cloneSymbol(bridgeSym);
	    }
	    bridgeSym.setType(Type.MethodType(params1, restp));

            bridges.append(genBridgeMethod(bridgeSym, sym));
        }

    }

    /** Add an "empty bridge" (abstract method declaration) to satisfy
     *  CLR's requirement that classes should provide declaration
     *  for all methods of the interfaces they implement
     */
    /*
    public void addEmptyBridge(Symbol owner, Symbol method) {
	Type bridgeType = method.nextType();
	Symbol bridgeSym = method.cloneSymbol(owner);
	bridgeSym.flags = bridgeSym.flags & ~JAVA | SYNTHETIC | DEFERRED;
	//bridgeSym.setOwner(owner);
	if (bridgeType instanceof Type.MethodType) {
            Type.MethodType methodType = (Type.MethodType)bridgeType;
            Symbol[] params = methodType.vparams;
            Type restp = methodType.result;
	    // assign to bridge symbol its bridge type
	    // where owner of all parameters is bridge symbol itself.
	    Symbol[] params1 = new Symbol[params.length];
	    for (int i = 0; i < params.length; i++) {
		params1[i] = params[i].cloneSymbol(bridgeSym);
	    }
	    bridgeSym.setType(Type.MethodType(params1, restp));
	    Tree bridge = gen.DefDef(bridgeSym, Tree.Empty);
            bridges.append(bridge);
	}
    }
    */

    private final Map interfaces/*<Symbol,Set<Symbol>>*/ = new HashMap();

    private Set getInterfacesOf(Symbol clasz) {
        assert clasz.isClass(): Debug.show(clasz);
        Set set = (Set)interfaces.get(clasz);
        if (set == null) {
            set = new HashSet();
            interfaces.put(clasz, set);
            Type parents[] = clasz.parents();
            for (int i = 0; i < parents.length; i++)
                set.addAll(getInterfacesOf(parents[i].symbol()));
            if (clasz.isInterface()) set.add(clasz);
        }
        return set;
    }

    private void addInterfaceBridges_(Symbol clasz) {
        assert clasz.isClass() && !clasz.isInterface(): Debug.show(clasz);
        assert clasz.parents().length > 0: Debug.show(clasz)+": "+clasz.info();
        Symbol svper = clasz.parents()[0].symbol();
        assert svper.isClass() && !svper.isInterface(): Debug.show(clasz);
        Set interfaces = new HashSet(getInterfacesOf(clasz));
        for (Iterator i = interfaces.iterator(); i.hasNext(); ) {
            Symbol inter = (Symbol)i.next();
            addInterfaceBridgesAux(clasz, inter.members());
        }
    }

    private void addInterfaceBridgesAux(Symbol owner, Scope symbols) {
        for (Scope.SymbolIterator i = symbols.iterator(true); i.hasNext();) {
            Symbol member = i.next();
            if (!member.isTerm() || !member.isDeferred()) continue;
            addInterfaceBridges(owner, member);
        }
    }

    private Symbol[] alternativesOf(Symbol symbol) {
        if (symbol == Symbol.NONE) return Symbol.EMPTY_ARRAY;
        Type info = symbol.type();
        if (info instanceof Type.OverloadedType)
            return ((Type.OverloadedType)info).alts;
        return new Symbol[] { symbol };
    }

    private Symbol[] declaredAlternativesOf(Symbol owner, Name name) {
        ArrayList alts = new ArrayList();
        if (currentMethodSyms != null) {
            ArrayList current = (ArrayList)currentMethodSyms.get(owner);
            if (current != null) {
                for (int i = 0; i < current.size(); i++) {
                    Symbol candidate = (Symbol)current.get(i);
                    if (candidate.name == name)
                        alts.add(candidate);
                }
            }
        }
        if (!alts.isEmpty())
            return (Symbol[])alts.toArray(new Symbol[alts.size()]);
        for (Scope.SymbolIterator i = owner.members().iterator(true); i.hasNext();) {
            Symbol candidate = i.next();
            if (candidate.owner() == owner && candidate.name == name)
                alts.add(candidate);
        }
        return (Symbol[])alts.toArray(new Symbol[alts.size()]);
    }

    private boolean isBridgeCandidate(Symbol symbol) {
        return symbol != Symbol.NONE
            && symbol.isTerm()
            && symbol.isMethod()
            && !symbol.isPrivate()
            && !symbol.isStatic()
            && !symbol.isInitializer();
    }

    private Type erasedMemberType(Type pre, Symbol symbol) {
        Type memberType = pre.memberType(symbol);
        if (memberType == Type.NoType || memberType == Type.ErrorType)
            return memberType;
        return memberType.derefDef().fullErasure();
    }

    private Type rawErasedType(Symbol symbol) {
        Type type = symbol.nextType();
        if (type == Type.NoType || type == Type.ErrorType)
            return type;
        return type.derefDef().fullErasure();
    }

    private Type bridgeTypeFor(Symbol owner, Symbol symbol) {
        Type type = Type.NoType;
        if (symbol.owner() == owner) {
            try {
                type = owner.thisType().memberType(symbol);
            } catch (Type.Malformed ex) {}
        }
        if (type == Type.NoType || type == Type.ErrorType)
            type = symbol.nextType();
        if (type == Type.NoType || type == Type.ErrorType)
            return type;
        return type.derefDef();
    }

    private boolean overridesAfterErasure(Type pre, Symbol overriding, Symbol overridden) {
        if (!isBridgeCandidate(overriding) || !isBridgeCandidate(overridden))
            return false;
        if (overriding.name != overridden.name)
            return false;
        Type overridingType = erasedMemberType(pre, overriding);
        Type overriddenType = erasedMemberType(pre, overridden);
        if (overridingType == Type.NoType || overridingType == Type.ErrorType ||
            overriddenType == Type.NoType || overriddenType == Type.ErrorType)
            return false;
        return isSubType(overridingType, overriddenType);
    }

    private boolean matchesAfterErasure(Type pre, Symbol overriding, Symbol overridden) {
        if (overridesAfterErasure(pre, overriding, overridden))
            return true;
        if (!isBridgeCandidate(overriding) || !isBridgeCandidate(overridden))
            return false;
        if (overriding.name != overridden.name)
            return false;
        Type overridingType = erasedMemberType(pre, overriding);
        Type overriddenType = erasedMemberType(pre, overridden);
        if (!(overridingType instanceof Type.MethodType) ||
            !(overriddenType instanceof Type.MethodType))
            return false;
        Type.MethodType overridingMethod = (Type.MethodType)overridingType;
        Type.MethodType overriddenMethod = (Type.MethodType)overriddenType;
        if (overridingMethod.vparams.length != overriddenMethod.vparams.length)
            return false;
        for (int i = 0; i < overridingMethod.vparams.length; i++)
            if (!hasSameJvmType(overridingMethod.vparams[i].type(),
                                overriddenMethod.vparams[i].type()))
                return false;
        return true;
    }

    private boolean hasSameErasedBridgeType(Type pre, Symbol sym1, Symbol sym2) {
        Type type1 = erasedMemberType(pre, sym1);
        Type type2 = erasedMemberType(pre, sym2);
        if (type1 == Type.NoType || type1 == Type.ErrorType ||
            type2 == Type.NoType || type2 == Type.ErrorType)
            return false;
        return type1.isSameAs(type2);
    }

    private boolean hasSameRawBridgeType(Symbol sym1, Symbol sym2) {
        Type type1 = rawErasedType(sym1);
        Type type2 = rawErasedType(sym2);
        if (type1 == Type.NoType || type1 == Type.ErrorType ||
            type2 == Type.NoType || type2 == Type.ErrorType)
            return false;
        return type1.isSameAs(type2);
    }

    private boolean hasSameBridgeType(Symbol owner, Symbol sym1, Symbol sym2) {
        return isSameAs(sym1.nextType(), sym2.nextType());
    }

    private boolean hasSameJvmType(Type type1, Type type2) {
        type1 = type1.fullErasure();
        type2 = type2.fullErasure();
        if (type1 == Type.NoType || type1 == Type.ErrorType ||
            type2 == Type.NoType || type2 == Type.ErrorType)
            return false;
        if (type1 instanceof Type.MethodType && type2 instanceof Type.MethodType) {
            Type.MethodType method1 = (Type.MethodType)type1;
            Type.MethodType method2 = (Type.MethodType)type2;
            if (method1.vparams.length != method2.vparams.length)
                return false;
            for (int i = 0; i < method1.vparams.length; i++) {
                if (!hasSameJvmType(method1.vparams[i].nextType(), method2.vparams[i].nextType()))
                    return false;
            }
            return hasSameJvmType(method1.result, method2.result);
        }
        if (type1 instanceof Type.UnboxedType && type2 instanceof Type.UnboxedType)
            return ((Type.UnboxedType)type1).tag == ((Type.UnboxedType)type2).tag;
        if (type1 instanceof Type.UnboxedArrayType && type2 instanceof Type.UnboxedArrayType)
            return hasSameJvmType(((Type.UnboxedArrayType)type1).elemtp,
                                  ((Type.UnboxedArrayType)type2).elemtp);
        return type1.symbol() == type2.symbol();
    }

    private Symbol findErasedOverrideInBase(Symbol method, Type base, Symbol owner) {
        if (base == Type.NoType || base == Type.ErrorType || base.symbol() == Symbol.NONE)
            return Symbol.NONE;
        Symbol deferred = Symbol.NONE;
        Symbol[] candidates = alternativesOf(base.symbol().members().lookup(method.name));
        Type site = owner.thisType();
        for (int i = 0; i < candidates.length; i++) {
            Symbol candidate = candidates[i];
            if (!matchesAfterErasure(site, method, candidate))
                continue;
            if (!candidate.isDeferred())
                return candidate;
            if (deferred == Symbol.NONE)
                deferred = candidate;
        }
        return deferred;
    }

    private Symbol findErasedOverridingMethod(Symbol owner, Symbol method) {
        Symbol deferred = Symbol.NONE;
        Symbol[] candidates = declaredAlternativesOf(owner, method.name);
        Type site = owner.thisType();
        for (int i = 0; i < candidates.length; i++) {
            Symbol candidate = candidates[i];
            if (!matchesAfterErasure(site, candidate, method))
                continue;
            if (!candidate.isDeferred())
                return candidate;
            if (deferred == Symbol.NONE)
                deferred = candidate;
        }
        return deferred;
    }

    private Symbol findErasedOverrideInParents(Symbol method, Type[] parents, Symbol owner) {
        for (int i = 0; i < parents.length; i++) {
            Symbol overridden = findErasedOverrideInBase(method, parents[i], owner);
            if (overridden != Symbol.NONE)
                return overridden;
            Symbol parent = parents[i].symbol();
            if (parent != Symbol.NONE) {
                overridden = findErasedOverrideInParents(method, parent.parents(), owner);
                if (overridden != Symbol.NONE)
                    return overridden;
            }
        }
        return Symbol.NONE;
    }

    private Symbol findErasedInterfaceOverride(Symbol method, Symbol owner) {
        return findErasedOverrideInParents(method, method.owner().parents(), owner);
    }

    private Symbol getOverriddenMethod(Symbol method) {
        Type[] parents = method.owner().parents();
        if (parents.length == 0) return Symbol.NONE;
        Symbol overridden = method.overriddenSymbol(parents[0]);
        if (overridden == Symbol.NONE ||
            !overridesAfterErasure(method.owner().thisType(), method, overridden))
            overridden = findErasedOverrideInBase(method, parents[0], method.owner());
        return overridden;
    }

    private Symbol getOverridingMethod(Symbol owner, Symbol method) {
        Symbol declared = findErasedOverridingMethod(owner, method);
        if (declared != Symbol.NONE)
            return declared;
        Symbol overriding = method.overridingSymbol(owner.thisType());
        if (overriding == Symbol.NONE ||
            overriding != method && !overridesAfterErasure(owner.thisType(), overriding, method))
            overriding = findErasedOverridingMethod(owner, method);
        return overriding;
    }

    public void addBridgeMethodsTo(Symbol method) {
        assert method.owner().isClass() && !method.owner().isInterface();
        Symbol overridden = getOverriddenMethod(method);
        if (!overridden.isNone() && !isSameAs(overridden.nextType(), method.nextType()))
            addBridge(method.owner(), method, overridden);
    }

    public void addInterfaceBridges(Symbol owner, Symbol method) {
	assert owner.isClass() && !owner.isInterface(): Debug.show(owner);
        Symbol overriding = getOverridingMethod(owner, method);
        if (overriding == method) {
            Symbol overridden = method.overriddenSymbol(owner.thisType().parents()[0], owner);
            if (!overridden.isNone() && !isSameAs(overridden.nextType(), method.nextType()))
                addBridge(owner, method, overridden);
	    // moved this into the TypeCreator class of the MSIL backend
  	    //if (forMSIL && (overridden.isNone() || overridden.owner() != owner))
  	    //	    addEmptyBridge(owner, method);
        } else if (!overriding.isNone() && !isSameAs(overriding.nextType(), method.nextType()))
            addBridge(owner, overriding, method);
    }

    private void addBridges(Symbol clasz, TreeList members) {
        TreeList savedBridges = bridges;
        HashMap savedBridgeSyms = bridgeSyms;
        HashMap savedCurrentMethodSyms = currentMethodSyms;
        bridges = new TreeList();
        bridgeSyms = new HashMap();
        currentMethodSyms = new HashMap();

        int length = members.length();
        ArrayList methodSyms = new ArrayList();
        for (int i = 0; i < length; i++) {
            if (members.get(i) instanceof Tree.DefDef)
                methodSyms.add(members.get(i).symbol());
        }
        currentMethodSyms.put(clasz, methodSyms);
        if (!clasz.isInterface()) {
            for (int i = 0; i < length; i++) {
                if (members.get(i) instanceof Tree.DefDef) {
                    addBridgeMethodsTo(members.get(i).symbol());
                }
            }
            addInterfaceBridges_(clasz);
        }

        members.append(bridges);
        if (bridges.length() > 0) {
            Type info = clasz.nextInfo();
            if (info instanceof Type.CompoundType) {
                Type.CompoundType compoundType = (Type.CompoundType)info;
                Scope members_ = compoundType.members.cloneScope();
                for (int i = 0; i < bridges.length(); i++) {
                    Tree bridge = (Tree)bridges.get(i);
                    members_.enterOrOverload(bridge.symbol());
                }
                clasz.updateInfo(Type.compoundType(compoundType.parts, members_, info.symbol()));
            } else {
                throw Debug.abort("class = " + Debug.show(clasz) + ", " +
                    "info = " + Debug.show(info));
            }
        }
        bridgeSyms = savedBridgeSyms;
        bridges = savedBridges;
        currentMethodSyms = savedCurrentMethodSyms;
    }


}
