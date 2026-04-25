/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**
** $Id$
\*                                                                      */
//todo: T {} == T

package scalac.symtab;

import java.util.HashMap;

import scala.tools.util.Position;
import scalac.ApplicationError;
import scalac.atree.AConstant;
import scalac.util.*;
import scalac.Global;

public class Type implements Modifiers, Kinds, TypeTags, EntryTags {

    public static boolean explainSwitch = false;
    private static int indent = 0;

    public static class ErrorType extends Type {
        public ErrorType() {}
    }  // not used after analysis
    public static final ErrorType ErrorType = new ErrorType();

    public static class AnyType extends Type {
        public AnyType() {}
    }    // not used after analysis
    public static final AnyType AnyType = new AnyType();

    public static class NoType extends Type {
        public NoType() {}
    }
    public static final NoType NoType = new NoType();

    public static class NoPrefix extends Type {
        public NoPrefix() {}
    }
    public static final NoPrefix NoPrefix = new NoPrefix();

    /** C.this.type
     */
    public static class ThisType extends Type {
        public Symbol sym;

        public ThisType(Symbol sym) {
            this.sym = sym;
            assert sym == Symbol.NONE || sym.isClassType(): Debug.show(sym);
        }
    }

    public static ThisType ThisType(Symbol sym) {
        return new ThisType(sym);
    }

    public static Type localThisType = ThisType(Symbol.NONE);

    /** pre.sym.type
     *  sym represents a valueS
     */
    public static class SingleType extends Type {
        public Type pre;
        public Symbol sym;

        public SingleType(Type pre, Symbol sym) {
            this.pre = pre;
            this.sym = sym;
            assert this instanceof ExtSingleType;
        }
    }

    /** Type for a numeric or string constant.
     */
    public static class ConstantType extends Type {
        public Type base;
        public AConstant value;

        public ConstantType(Type base, AConstant value) {
            this.base = base;
            this.value = value;
        }
    }

    public static ConstantType ConstantType(Type base, AConstant value) {
        return new ConstantType(base, value);
    }

    /** pre.sym[args]
     *  sym represents a type
     *  for example: scala.List[java.lang.String] is coded as
     *
     *  TypeRef(
     *      SingleType(ThisType(definitions.ROOT_CLASS), definitions.SCALA),
     *      <List>,
     *      new Type[]{
     *          TypeRef(
     *              SingleType(
     *                  SingleType(ThisType(definitions.ROOT_CLASS), definitions.JAVA),
     *                  definitions.LANG),
     *              definitions.STRING,
     *              new Type[]{})}).
     *
     */
    public static class TypeRef extends Type {
        public Type pre;
        public Symbol sym;
        public Type[] args;

        public TypeRef(Type pre, Symbol sym, Type[] args) {
            this.pre = pre;
            this.sym = sym;
            this.args = args;
            assert this instanceof ExtTypeRef: this;
        }
    }

    public static TypeRef TypeRef(Type pre, Symbol sym, Type[] args) {
        return new TypeRef(pre, sym, args);
    }

    /** parts_1 with ... with parts_n { members }
     */
    public static class CompoundType extends Type {
        public Type[] parts;
        public Scope members;

        public CompoundType(Type[] parts, Scope members) {
            this.parts = parts;
            this.members = members;
            assert this instanceof ExtCompoundType;
        }
    }

    /** synthetic type of a method  def ...(vparams): result = ...
     */
    public static class MethodType extends Type {
        public Symbol[] vparams;
        public Type result;

        public MethodType(Symbol[] vparams, Type result) {
            this.vparams = vparams;
            this.result = result;
            for (int i = 0; i < vparams.length; i++)
                assert vparams[i].isParameter() && vparams[i].isTerm(): this;
        }
    }

    public static MethodType MethodType(Symbol[] vparams, Type result) {
        return new MethodType(vparams, result);
    }

    /** synthetic type of a method  def ...[tparams]result
     *  For instance, given  def f[a](x: a): a
     *  f has type   PolyType(new Symbol[]{<a>},
     *                 MethodType(new Symbol[]{<x>}, <a>.type()))
     *
     *  if tparams is empty, this is the type of a parameterless method
     *  def ... =
     *  For instance, given    def f = 1
     *  f has type   PolyType(new Symbol[]{}, <scala.Int>.type())
     */
    public static class PolyType extends Type {
        public Symbol[] tparams;
        public Type result;

        public PolyType(Symbol[] tparams, Type result) {
            this.tparams = tparams;
            this.result = result;
            for (int i = 0; i < tparams.length; i++)
                assert tparams[i].isParameter()&&tparams[i].isAbstractType(): this;
        }
    }

    public static PolyType PolyType(Symbol[] tparams, Type result) {
        return new PolyType(tparams, result);
    }

    /** synthetic type of an overloaded value whose alternatives are
     *  alts_1, ..., alts_n, with respective types alttypes_1, ..., alttypes_n
     *
     *  For instance, if there are two definitions of `f'
     *    def f: int
     *    def f: String
     *  then there are three symbols:
     *    ``f1'' corresponding to def f: int
     *    ``f2'' corresponding to def f: String
     *    ``f3'' corresponding to both
     *  f3 has type
     *    OverloadedType(
     *      new Symbol[]{<f1>, <f2>},
     *      new Type[]{PolyType(new Symbol[]{}, <int>),
     *                 PolyType(new Symbol[]{}, <String>),
     *
     */
    public static class OverloadedType extends Type {
        public Symbol[] alts;
        public Type[] alttypes;

        public OverloadedType(Symbol[] alts, Type[] alttypes) {
            this.alts = alts;
            this.alttypes = alttypes;
        }
    }

    public static OverloadedType OverloadedType(Symbol[] alts, Type[] alttypes) {
        return new OverloadedType(alts, alttypes);
    }

    /** Hidden case to implement delayed evaluation of types.
     *  No need to pattern match on this type; it will never come up.
     */
    public static class LazyType extends Type {
        public LazyType() {}
    }

    /** Hidden case to implement local type inference.
     *  Later phases do not need to match on this type.
     */
    public static class TypeVar extends Type {
        public Type origin;
        public Constraint constr;

        public TypeVar(Type origin, Constraint constr) {
            this.origin = origin;
            this.constr = constr;
        }
    }

    public static TypeVar TypeVar(Type origin, Constraint constr) {
        return new TypeVar(origin, constr);
    }

    /** Hidden cases to implement type erasure.
     *  Earlier phases do not need to match on these types.
     */
    public static class UnboxedType extends Type {
        public int tag;

        public UnboxedType(int tag) {
            this.tag = tag;
        }
    }

    public static UnboxedType UnboxedType(int tag) {
        return new UnboxedType(tag);
    }

    public static class UnboxedArrayType extends Type {
        public Type elemtp;

        public UnboxedArrayType(Type elemtp) {
            this.elemtp = elemtp;
        }
    }

    public static UnboxedArrayType UnboxedArrayType(Type elemtp) {
        return new UnboxedArrayType(elemtp);
    }

    /** Force evaluation of a lazy type. No cycle
     *  check is needed; since this is done in Symbol.
     *  @see  Symbol.info().
     */
    public void complete(Symbol p) {}

// Creators ---------------------------------------------------------------------

    /** An empty Type array */
    public static final Type[] EMPTY_ARRAY  = new Type[0];

    public static SingleType singleType(Type pre, Symbol sym) {
        assert sym.isTerm() && !sym.isNone(): pre + " -- " + Debug.show(sym);
        rebind:
        {
            Symbol owner = sym.owner();
            if (!owner.isClass()) break rebind;
            if (owner == pre.symbol()) break rebind;
            // !!! add if (owner is sealed/final) break rebind ?
            // !!! add if (owner is module class) break rebind ?
            if (sym.isFinal() || sym.isPrivate()) break rebind;
            Symbol rebind = pre.lookupNonPrivate(sym.name);
            if (rebind.isNone()) break rebind;
            if (rebind.isLocked()) throw new Type.Error(
                "illegal cyclic reference involving " + rebind);
            sym = rebind.rebindSym();
        }
        if (pre.isStable() || pre.isError()) {
            return new ExtSingleType(pre, sym);
        } else {
            throw new Type.Malformed(pre, sym.nameString() + ".type");
        }
    }

    public static Type constantType(AConstant value) {
        return Global.instance.definitions.atyper.type(value);
    }

    public static Type singleTypeMethod(Type pre, Symbol sym) {
        Global global = Global.instance;
        if (global.currentPhase.id <= global.PHASE.UNCURRY.id())
            return singleType(pre, sym);
        else if (global.currentPhase.id <= global.PHASE.ERASURE.id())
            return sym.type().singleTypeMethod0(pre, sym);
        else
            return pre.memberType(sym);
    }

    private Type singleTypeMethod0(Type pre, Symbol sym) {
        if (this instanceof PolyType) {
            PolyType polyType = (PolyType)this;
            return PolyType(polyType.tparams, polyType.result.singleTypeMethod0(pre, sym));
        } else if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
            return MethodType(methodType.vparams, methodType.result.singleTypeMethod0(pre, sym));
        }
        return singleType(pre, sym);
    }

    public static Type appliedType(Type tycon, Type[] args) {
        if (tycon instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tycon;
            if (args == typeRef.args) return typeRef;
            else return Type.typeRef(typeRef.pre, typeRef.sym, args);
        }
        throw new ApplicationError();
    }

    public static Type typeRef(Type pre, Symbol sym, Type[] args) {
        if (sym.kind == TYPE && !pre.isLegalPrefix() && !pre.isError())
            throw new Type.Malformed(pre, sym.nameString());
        rebind:
        if (sym.isAbstractType()) {
            Symbol owner = sym.owner();
            if (!owner.isClass()) break rebind;
            if (owner == pre.symbol()) break rebind;
            // !!! add if (owner is sealed/final) break rebind ?
            // !!! add if (owner is module class) break rebind ?
            if (sym.isFinal() || sym.isPrivate()) break rebind;
            Symbol rebind = pre.lookupNonPrivate(sym.name);
            if (rebind.isNone()) break rebind;
            if (rebind.isLocked()) throw new Type.Error(
                "illegal cyclic reference involving " + rebind);
            sym = rebind.rebindSym();
        }
        if (sym.isTypeAlias()) {
            Symbol[] params = sym.typeParams();
            if (args.length == params.length)
                return pre.memberInfo(sym).subst(params, args);
            assert args.length == 0 || args.length == params.length:
                Debug.show(pre, sym, args, params);
        }
        assert isLegalTypeRef(pre, sym, args):
            Debug.show(pre, sym, args, sym.typeParams());
        return new ExtTypeRef(pre, sym, args);
    }
    private static boolean isLegalTypeRef(Type pre, Symbol sym, Type[] args) {
        if (sym.kind == TYPE && !pre.isLegalPrefix() && !pre.isError()) return false;
        if (!sym.isType() && !sym.isError()) return false;
        // !!! return args.length == 0 || args.length == sym.typeParams().length;
        return true;
    }

    public static Type newTypeRefUnsafe(Type pre, Symbol sym, Type[] args) {
        return new ExtTypeRef(pre, sym, args);
    }

    public static CompoundType compoundType(Type[] parts, Scope members,
                                            Symbol clazz) {
        return new ExtCompoundType(parts, members, clazz);
    }

    public static CompoundType compoundTypeWithOwner(Symbol owner, Type[] parts, Scope members) {
        return new ExtCompoundType(owner, parts, members);
    }

    static class ExtSingleType extends SingleType {
        Type tp = null;
        int definedId = -1;
        ExtSingleType(Type pre, Symbol sym) {
            super(pre, sym);
        }
        public Type singleDeref() {
            if (definedId != Global.instance.currentPhase.id) {
                definedId = Global.instance.currentPhase.id;
                tp = pre.memberType(sym).resultType();
            }
            return tp;
        }
    }

    static class ExtTypeRef extends TypeRef {
        ExtTypeRef(Type pre, Symbol sym, Type[] args) {
            super(pre, sym, args);
        }
    }

    private static final class ExtCompoundType extends CompoundType {
        private final Symbol clasz;
        public ExtCompoundType(Symbol owner, Type[] parts, Scope members) {
            super(parts, members);
            this.clasz = owner.newCompoundClass(this);
	    assert !owner.isPackageClass() : ArrayApply.toString(parts);
        }
        public ExtCompoundType(Type[] parts, Scope members, Symbol clasz) {
            super(parts, members);
            this.clasz = clasz;
        }
        public Symbol symbol() {
            return clasz;
        }
    }

// Access methods ---------------------------------------------------------------

    /** If this is a thistype, named type, applied type, singleton type, or compound type,
     *  its symbol, otherwise Symbol.NONE.
     */
    public Symbol symbol() {
        if (this instanceof ThisType) {
            return ((ThisType)this).sym;
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym;
        } else if (this instanceof SingleType) {
            return ((SingleType)this).sym;
        } else if (this instanceof ConstantType) {
            return ((ConstantType)this).base.symbol();
        } else if (this instanceof TypeVar) {
            return ((TypeVar)this).origin.symbol();
        } else if (this instanceof CompoundType) {
            // overridden in ExtCompoundType
            throw new ApplicationError();
        }
        return Symbol.NONE;
    }

    public static Symbol[] symbol(Type[] tps) {
        Symbol[] syms = new Symbol[tps.length];
        for (int i = 0; i < syms.length; i++)
            syms[i] = tps[i].symbol();
        return syms;
    }

    /** If this is a reference to a type constructor, add its
     *  type parameters as arguments
     */
    public Type withDefaultArgs() {
        if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            if (typeRef.args.length == 0 && typeRef.sym.typeParams().length != 0)
                return Type.typeRef(typeRef.pre, typeRef.sym, Symbol.type(typeRef.sym.typeParams()));
        }
        return this;
    }

    /** The upper bound of this type. Returns always a TypeRef whose
     * symbol is a class.
     */
    public Type bound() {
        Type tp = unalias();
        if (tp instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tp;
            if (typeRef.sym.kind == TYPE) return typeRef.pre.memberInfo(typeRef.sym).bound();
            assert typeRef.sym.isClass() : Debug.show(typeRef.sym) + " -- " + this;
            return this;
        } else if (tp instanceof ThisType
                   || tp instanceof SingleType
                   || tp instanceof ConstantType) {
            return singleDeref().bound();
        } else if (tp instanceof TypeVar) {
            Constraint constr = ((TypeVar)tp).constr;
            if (constr.inst != NoType) return constr.inst.bound();
            else return this;
        }
        throw Debug.abort("illegal case", this);
    }

    /** If this type is a thistype or singleton type, its type,
     *  otherwise the type itself.
     */
    public Type singleDeref() {
        if (this instanceof ThisType) {
            return ((ThisType)this).sym.typeOfThis();
        } else if (this instanceof SingleType) {
            // overridden in ExtSingleType
            throw new ApplicationError();
        } else if (this instanceof ConstantType) {
            return ((ConstantType)this).base;
        } else if (this instanceof TypeVar) {
            Constraint constr = ((TypeVar)this).constr;
            if (constr.inst != NoType) return constr.inst.singleDeref();
            else return this;
        }
        return this;
    }

    /** If this type is a thistype or singleton type, its underlying object type,
     *  otherwise the type itself.
     */
    public Type widen() {
        Type tp = singleDeref();
        if (tp instanceof ThisType
            || tp instanceof SingleType
            || tp instanceof ConstantType) {
            return tp.widen();
        }
        return tp;
    }

    private static Map widenMap = new Map() {
            public Type apply(Type t) {
                return t.widen();
            }
        };

    public static Type[] widen(Type[] tps) {
        return widenMap.map(tps);
    }

    /** The thistype or singleton type corresponding to values of this type.
      */
    public Type narrow() {
        Type tp = unalias();
        if (tp instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tp;
            if (typeRef.sym.kind == CLASS) return typeRef.sym.thisType();
            else return ThisType(typeRef.sym);
        } else if (tp instanceof CompoundType) {
            return symbol().thisType();
        }
        return this;
    }

    /** If this type is a constant type, its underlying basetype;
     *  otherwise the type itself
     */
    public Type deconst() {
        if (this instanceof ConstantType) {
            return ((ConstantType)this).base;
        }
        return this;
    }

    /** If this type is a parameterless method, its underlying resulttype;
     *  otherwise the type itself
     */
    public Type derefDef() {
        if (this instanceof PolyType) {
            PolyType polyType = (PolyType)this;
            if (polyType.tparams.length == 0) return polyType.result;
        }
        return this;
    }

    /** The lower approximation of this type (which must be a typeref)
     */
    public Type loBound() {
        Type tp = unalias();
        if (tp instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tp;
            Type lb = Global.instance.definitions.ALL_TYPE();
            if (typeRef.sym.kind == TYPE) {
                lb = typeRef.pre.memberLoBound(typeRef.sym);
            }
            if (lb.symbol() == Global.instance.definitions.ALL_CLASS &&
                this.symbol() != Global.instance.definitions.ALL_CLASS &&
                this.isSubType(Global.instance.definitions.ANYREF_TYPE())) {
                lb = Global.instance.definitions.ALLREF_TYPE();
            }
            return lb;
        }
        throw new ApplicationError();
    }

    /** If this is a this-type, named-type, applied type or single-type, its prefix,
     *  otherwise NoType.
     */
    public Type prefix() {
        if (this instanceof ThisType) {
            return ((ThisType)this).sym.owner().thisType();
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).pre;
        } else if (this instanceof SingleType) {
            return ((SingleType)this).pre;
        } else if (this instanceof TypeVar) {
            TypeVar typeVar = (TypeVar)this;
            Constraint constr = typeVar.constr;
            if (constr.inst != NoType) return constr.inst.prefix();
            else return NoType;
        }
        return NoType;
    }

   /** Get all type arguments of this type.
    */
    public Type[] typeArgs() {
        Type tp = unalias();
        if (tp instanceof TypeRef) {
            return ((TypeRef)tp).args;
        }
        return Type.EMPTY_ARRAY;
    }

    /** Get type of `this' symbol corresponding to this type, extend
     *  homomorphically to function types and poly types.
     */
    public Type instanceType() {
        Type tp = unalias();
        if (tp instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tp;
            if (typeRef.sym != typeRef.sym.thisSym())
                return typeRef.sym.typeOfThis()
                    .asSeenFrom(typeRef.pre, typeRef.sym.owner())
                    .subst(typeRef.sym.typeParams(), typeRef.args);
        } else if (tp instanceof MethodType) {
            MethodType methodType = (MethodType)tp;
            Type restp1 = methodType.result.instanceType();
            if (restp1 != methodType.result)
                return MethodType(methodType.vparams, restp1);
        } else if (tp instanceof PolyType) {
            PolyType polyType = (PolyType)tp;
            Type restp1 = polyType.result.instanceType();
            if (restp1 != polyType.result)
                return PolyType(polyType.tparams, restp1);
        }
        return this;
    }

    /** Remove all aliases
     */
    public Type unalias() {
        Type result = unalias(0);//debug
        //if (this != result) System.out.println(this + " ==> " + result);//DEBUG
        return result;
    }

    private Type unalias(int n) {
        if (n == 100)
            throw new Type.Error("alias chain too long (recursive type alias?): " + this);
        if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            if (typeRef.sym.kind == ALIAS && typeRef.sym.typeParams().length == typeRef.args.length)
                return typeRef.sym.info().subst(typeRef.sym.typeParams(), typeRef.args)
                    .asSeenFrom(typeRef.pre, typeRef.sym.owner()).unalias(n + 1);
        } else if (this instanceof TypeVar) {
            Constraint constr = ((TypeVar)this).constr;
            if (constr.inst != NoType) return constr.inst.unalias(n + 1);
            else return this;
        }
        return this;
    }

    /** The (prefix/argument-adapted) parents of this type.
     */
    public Type[] parents() {
        Type tp = unalias();
        if (tp instanceof ThisType
            || tp instanceof SingleType
            || tp instanceof ConstantType) {
            return singleDeref().parents();
        } else if (tp instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)tp;
            if (typeRef.sym.kind == CLASS) {
                assert typeRef.sym.typeParams().length == typeRef.args.length : typeRef.sym + " " + ArrayApply.toString(typeRef.args) + " " + typeRef.sym.primaryConstructor().info();//debug
                return subst(asSeenFrom(typeRef.sym.info().parents(), typeRef.pre, typeRef.sym.owner()),
                             typeRef.sym.typeParams(), typeRef.args);
            } else {
                return new Type[]{typeRef.sym.info().asSeenFrom(typeRef.pre, typeRef.sym.owner())};
            }
        } else if (tp instanceof CompoundType) {
            return ((CompoundType)tp).parts;
        }
        return Type.EMPTY_ARRAY;
    }

    /** Get type parameters of method type (a PolyType or MethodType)
     * or EMPTY_ARRAY if method type is not polymorphic.
     */
    public Symbol[] typeParams() {
        if (this instanceof PolyType) {
            return ((PolyType)this).tparams;
        } else if (this instanceof MethodType) {
            return Symbol.EMPTY_ARRAY;
        } else if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            if (typeRef.args.length == 0) return typeRef.sym.typeParams();
            else return Symbol.EMPTY_ARRAY;
        }
        return Symbol.EMPTY_ARRAY;
    }

    /** Get value parameters of method type (a PolyType or MethodType)
     * or EMPTY_ARRAY if method type has no value parameter section.
     */
    public Symbol[] valueParams() {
        return valueParams(false);
    }
    private Symbol[] valueParams(boolean ok) {
        if (this instanceof PolyType) {
            return ((PolyType)this).result.valueParams(true);
        } else if (this instanceof MethodType) {
            return ((MethodType)this).vparams;
        }
        if (ok) return Symbol.EMPTY_ARRAY;
        throw Debug.abort("illegal case", this);
    }

    /** If this type is a (possibly polymorphic) method type, its result type
     *  after applying all method argument sections,
     *  otherwise the type itself.
     */
    public Type resultType() {
        if (this instanceof PolyType) {
            return ((PolyType)this).result.resultType();
        } else if (this instanceof MethodType) {
            return ((MethodType)this).result.resultType();
        }
        return this;
    }

    /** The number of value parameter sections of this type.
     */
    public int paramSectionCount() {
        if (this instanceof PolyType) {
            return ((PolyType)this).result.paramSectionCount();
        } else if (this instanceof MethodType) {
            return ((MethodType)this).result.paramSectionCount() + 1;
        }
        return 0;
    }

    /** The first parameter section of this type.
     */
    public Symbol[] firstParams() {
        if (this instanceof PolyType) {
            return ((PolyType)this).result.firstParams();
        } else if (this instanceof MethodType) {
            return ((MethodType)this).vparams;
        }
        return Symbol.EMPTY_ARRAY;
    }

    /** If this type is overloaded, its alternative types,
     *  otherwise an array consisting of this type itself.
     */
    public Type[] alternativeTypes() {
        if (this instanceof OverloadedType) {
            return ((OverloadedType)this).alttypes;
        }
        return new Type[]{this};
    }

    /** If this type is overloaded, its alternative symbols,
     *  otherwise an empty array.
     */
    public Symbol[] alternativeSymbols() {
        if (this instanceof OverloadedType) {
            return ((OverloadedType)this).alts;
        }
        return Symbol.EMPTY_ARRAY;
    }

    /** If type is a this type of a module class, transform to singletype of
     *  module.
     */
    public Type expandModuleThis() {
        if (this instanceof ThisType) {
            Symbol sym = ((ThisType)this).sym;
            if (sym.isModuleClass()) {
                return singleType(
                    sym.owner().thisType().expandModuleThis(),
                    sym.sourceModule());
            }
        }
        return this;
    }

// Tests --------------------------------------------------------------------

    /** Is this type a an error type?
     */
    public boolean isError() {
        if (this == ErrorType) {
            return true;
        } else if (this instanceof ThisType) {
            return ((ThisType)this).sym.isError();
        } else if (this instanceof SingleType) {
            return ((SingleType)this).sym.isError();
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym.isError();
        } else if (this instanceof CompoundType) {
            return symbol().isError();
        }
        return false;
    }

    /** Is this type a this type or singleton type?
     */
    public boolean isStable() {
        Type tp = unalias();
        if (tp == NoPrefix
            || tp instanceof ThisType
            || tp instanceof SingleType
            || tp instanceof ConstantType) {
            return true;
        } else if (tp instanceof TypeRef) {
            Symbol sym = ((TypeRef)tp).sym;
            if (sym.isParameter() && sym.isSynthetic() && sym.hasStableFlag()) return true;
            return false;
        }
        return false;
    }

    /** Is this type a legal prefix?
     */
    public boolean isLegalPrefix() {
        Type tp = unalias();
        if (tp == NoPrefix || tp instanceof ThisType || tp instanceof SingleType) {
            return true;
        } else if (tp instanceof TypeRef) {
            Symbol sym = ((TypeRef)tp).sym;
            if (sym.isParameter() && sym.isSynthetic()) return true;
	    return false;
	    /*
            return sym.kind == CLASS &&
                ((sym.flags & JAVA) != 0 ||
                 (sym.flags & (TRAIT | ABSTRACT)) == 0);
	    */
        }
        return false;
    }

    /** Is this type a reference to an object type?
     *  todo: replace by this.isSubType(global.definitions.ANY_TYPE())?
     */
    public boolean isObjectType() {
        Type tp = unalias();
        if (tp instanceof ThisType
            || tp instanceof SingleType
            || tp instanceof ConstantType
            || tp instanceof CompoundType
            || tp instanceof TypeRef) {
            return true;
        }
        return false;
    }

    /** Is this type of the form scala.FunctionN[T_1, ..., T_n, +T] or
     *  scala.AnyRef with scala.FunctionN[T_1, ..., T_n, +T] or
     *  java.lang.Object with scala.FunctionN[T_1, ..., T_n, +T]?
     */
    public boolean isFunctionType() {
        if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            Definitions definitions = Global.instance.definitions;
            return typeRef.args.length > 0
                && typeRef.args.length <= definitions.FUNCTION_COUNT
                && typeRef.sym == definitions.FUNCTION_CLASS[typeRef.args.length - 1];
        } else if (this instanceof CompoundType) {
            CompoundType compoundType = (CompoundType)this;
            Definitions definitions = Global.instance.definitions;
            return compoundType.members.isEmpty() &&
                compoundType.parts.length == 2 &&
                (compoundType.parts[0].symbol() == definitions.OBJECT_CLASS ||
			 compoundType.parts[0].symbol() == definitions.ANYREF_CLASS) &&
                compoundType.parts[1].isFunctionType();
        }
        return false;
    }

    /** Is this a polymorphic method type?
     */
    public boolean isPolymorphic() {
        return typeParams().length > 0;
    }

    /** Is this a parameterized or polymorphic method type?
     */
    public boolean isParameterized() {
        return this instanceof MethodType || isPolymorphic();
    }

// Members and Lookup -------------------------------------------------------

    /** Get the scope containing the local members of this type.
     *  Symbols in this scope are not prefix-adapted!
     */
    public Scope members() {
        if (this == ErrorType) {
            return new Scope();
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym.info().members();
        } else if (this instanceof SingleType || this instanceof ConstantType) {
            return singleDeref().members();
        } else if (this instanceof CompoundType) {
            return ((CompoundType)this).members;
        }
        return Scope.EMPTY;
    }

    /** Lookup symbol with given name among all local and inherited members
     *  of this type; return Symbol.NONE if not found.
     */
    public Symbol lookup(Name name) {
        if (this == ErrorType) {
            return new ErrorScope(Symbol.NONE).lookup(name);
        } else if (this instanceof ThisType
                   || this instanceof SingleType
                   || this instanceof ConstantType) {
            return singleDeref().lookup(name);
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym.info().lookup(name);
        } else if (this instanceof CompoundType) {
            Scope members = ((CompoundType)this).members;
            Symbol sym = members.lookup(name);
            if (sym.kind != NONE) return sym;
            else return lookupNonPrivate(name);
        }
        return Symbol.NONE;
    }

    /** Lookup non-private symbol with given name among all local and
     *  inherited members of this type; return Symbol.NONE if not found.
     */
    public Symbol lookupNonPrivate(Name name) {
        if (this == ErrorType) {
            return new ErrorScope(Symbol.NONE).lookup(name);
        } else if (this instanceof ThisType
                   || this instanceof SingleType
                   || this instanceof ConstantType) {
            return singleDeref().lookupNonPrivate(name);
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym.info().lookupNonPrivate(name);
        } else if (this instanceof CompoundType) {
            CompoundType compoundType = (CompoundType)this;
            Type[] parts = compoundType.parts;
            Scope members = compoundType.members;
            Symbol sym = members.lookup(name);
            if (sym.kind != NONE && (sym.flags & PRIVATE) == 0)
                return sym;

            // search base types in reverse; non-abstract members
            // take precedence over abstract ones.
            int i = parts.length;
            sym = Symbol.NONE;
            while (i > 0) {
                i--;
                Symbol sym1 = parts[i].lookupNonPrivate(name);
                if (sym1.kind != NONE &&
                    (sym1.flags & PRIVATE) == 0 &&
                    (sym.kind == NONE
		     ||
		     (sym.flags & DEFERRED) != 0 &&
		     (sym1.flags & DEFERRED) == 0
		     ||
		     (sym.flags & DEFERRED) == (sym1.flags & DEFERRED) &&
		     sym1.owner().isSubClass(sym.owner())))
                    sym = sym1;
            }
            return sym;
        }
        return Symbol.NONE;
    }

    public static Symbol lookupNonPrivate(Type[] parts, Name name) {
	// search base types in reverse; non-abstract members
	// take precedence over abstract ones.
	int i = parts.length;
	Symbol sym = Symbol.NONE;
	while (i > 0) {
	    i--;
	    Symbol sym1 = parts[i].lookupNonPrivate(name);
	    if (sym1.kind != NONE &&
		(sym.kind == NONE
		 ||
		 (sym.flags & DEFERRED) != 0 &&
		 (sym1.flags & DEFERRED) == 0
		 ||
		 (sym.flags & DEFERRED) == (sym1.flags & DEFERRED) &&
		 sym1.owner().isSubClass(sym.owner())))
		sym = sym1;
	}
	return sym;
    }

    /**
     * Looks up in the current type a symbol with the same name as the
     * given symbol and whose type (as seen from the given prefix) is
     * in the given relation to the type (as seen from the given
     * prefix) of the given symbol. If no such symbol is found,
     * returns NONE. Note that in some cases, the returned symbol may
     * be equal to the given one. The main purpose of this method is
     * look up overridden and overriding symbols.
     */
    public Symbol lookup(Symbol sym, Type pre, Relation relation) {
        assert !sym.isOverloaded(): Debug.show(sym);
        if (sym.isPrivate() || sym.isStatic() || sym.isInitializer())
            return symbol().isSubClass(sym.owner()) ? sym : Symbol.NONE;
        Type symtype = pre.memberType(sym).derefDef();
        Symbol[] classes = classes();
        Symbol deferred = null;
        for (int i = 0; i < classes.length; i++) {
            if (deferred != null && deferred.isSubClass(classes[i])) continue;
            Symbol sym1 = classes[i].members().lookup(sym.name);
            Type sym1Type = sym1.type();
            if (sym1Type == NoType || sym1Type == ErrorType) {
                continue;
            } else if (sym1Type instanceof OverloadedType) {
                Symbol[] alts = ((OverloadedType)sym1Type).alts;
                for (int j = 0; j < alts.length; j++)
                    if (areRelated(sym, symtype, relation, pre,alts[j],false)){
                        if (!alts[j].isDeferred()) return alts[j];
                        if (deferred == null) deferred = alts[j];
                    }
                continue;
            } else {
                if (areRelated(sym, symtype, relation, pre, sym1, true)) {
                    if (!sym1.isDeferred()) return sym1;
                    if (deferred == null) deferred = sym1;
                }
                continue;
            }
        }
        return deferred == null ? Symbol.NONE : deferred;
    }
    //where
    private static boolean areRelated(
        Symbol sym, Type symtype, Relation relation, Type pre, Symbol sym1,
        boolean warn)
    {
        if (sym == sym1) return true;
        if (sym1.isPrivate() || sym1.isStatic() || sym1.isInitializer()) return false;
//         System.out.println("Is 'sym1' " + relation + " 'sym' in 'pre' ?"
//             + "\n  sym      : " + Debug.show(sym)
//             + "\n  sym1     : " + Debug.show(sym1)
//             + "\n  sym .type: " + sym.type()
//             + "\n  sym1.type: " + sym1.type()
//             + "\n  pre      : " + pre
//         );//DEBUG
        Type sym1type = pre.memberType(sym1).derefDef();
        if (sym1.isJava()) symtype = symtype.objParamToAny();
        if (sym1type.compareTo(symtype, relation)) return true;
        if (warn && Global.instance.debug) System.out.println(
            "'sym1' is not " + relation + " 'sym' in 'pre'"
            + "\n  sym      : " + Debug.show(sym)
            + "\n  sym1     : " + Debug.show(sym1)
            + "\n  sym .type: " + sym.type()
            + "\n  sym1.type: " + sym1.type()
            + "\n  pre      : " + pre
            + "\nsince 'sym1type' " + relation.toString(true) + " 'symtype'"
            + "\n  symtype  : " + symtype
            + "\n  sym1type : " + sym1type
        );//DEBUG
        return false;
    }
    private Symbol[] classes() {
        if (this instanceof ThisType
                   || this instanceof SingleType
                   || this instanceof ConstantType) {
            return singleDeref().classes();
        } else if (this instanceof TypeRef) {
            return ((TypeRef)this).sym.info().classes();
        } else if (this instanceof CompoundType) {
            return symbol(symbol().closure());
        }
        return Symbol.EMPTY_ARRAY;
    }
    static private Map objToAnyMap = new Map() {
	public Type apply(Type t) {
	    if (t.symbol() == Global.instance.definitions.OBJECT_CLASS)
		return Global.instance.definitions.ANY_TYPE();
	    else return t;
	}
    };

    private Type objParamToAny() {
	if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
	    Symbol[] params1 = objToAnyMap.map(methodType.vparams);
	    if (params1 == methodType.vparams) return this;
	    else return MethodType(params1, methodType.result);
	}
	return this;
    }

// Set Owner ------------------------------------------------------------------

    public Type setOwner(Symbol owner) {
        if (this instanceof PolyType) {
            PolyType polyType = (PolyType)this;
            Type restpe1 = polyType.result.setOwner(owner);
            if (restpe1 == polyType.result) return this;
            else return Type.PolyType(polyType.tparams, restpe1);
        } else if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
            Symbol[] params = methodType.vparams;
            Symbol[] params1 = params;
            if (params.length > 0 &&
                params[0].owner() != owner && params[0].owner() != Symbol.NONE) {
                params1 = new Symbol[params.length];
                for (int i = 0; i < params.length; i++)
                    params1[i] = params[i].cloneSymbol();
            }
            for (int i = 0; i < params.length; i++)
                params1[i].setOwner(owner);
            Type restpe1 = methodType.result.setOwner(owner);
            if (params1 == params && restpe1 == methodType.result) return this;
            else return Type.MethodType(params1, restpe1);
        }
        return this;
    }

// Maps --------------------------------------------------------------------------

    /** The type of type-to-type functions.
     */
    public abstract static class Map {

        public abstract Type apply(Type t);

        /**
         * This method assumes that all symbols in MethodTypes and
         * PolyTypes have already been cloned.
         */
        public Type applyParams(Type type) {
            if (type instanceof MethodType) {
                MethodType methodType = (MethodType)type;
                map(methodType.vparams, true);
                Type result1 = applyParams(methodType.result);
                return methodType.result == result1 ? type : MethodType(methodType.vparams, result1);
            } else if (type instanceof PolyType) {
                PolyType polyType = (PolyType)type;
                map(polyType.tparams, true);
                Type result1 = applyParams(polyType.result);
                return polyType.result == result1 ? type : PolyType(polyType.tparams, result1);
            }
            return apply(type);
        }

        /** Apply map to all top-level components of this type.
         */
        public Type map(Type tp) {
            if (tp == ErrorType
                || tp == AnyType
                || tp == NoType
                || tp == NoPrefix
                || tp instanceof UnboxedType
                || tp instanceof TypeVar
                || tp instanceof ThisType) {
                return tp;
            } else if (tp instanceof TypeRef) {
                TypeRef typeRef0 = (TypeRef)tp;
                Type pre1 = apply(typeRef0.pre);
                Type[] args1 = map(typeRef0.args);
                if (pre1 == typeRef0.pre && args1 == typeRef0.args) return tp;
                else return typeRef(pre1, typeRef0.sym, args1);
            } else if (tp instanceof SingleType) {
                SingleType singleType0 = (SingleType)tp;
                Type pre1 = apply(singleType0.pre);
                if (pre1 == singleType0.pre) return tp;
                else return singleType(pre1, singleType0.sym);
            } else if (tp instanceof ConstantType) {
                ConstantType constantType = (ConstantType)tp;
                Type base1 = apply(constantType.base);
                if (base1 == constantType.base) return tp;
                else return new ConstantType(base1, constantType.value);
            } else if (tp instanceof CompoundType) {
                CompoundType compoundType = (CompoundType)tp;
                Type[] parts1 = map(compoundType.parts);
                Scope members1 = map(compoundType.members);
                if (parts1 == compoundType.parts && members1 == compoundType.members) {
                    return tp;
                } else if (members1 == compoundType.members && !tp.symbol().isCompoundSym()) {
                    return compoundType(parts1, compoundType.members, tp.symbol());
                } else {
                    Scope members2 = new Scope();
                    //Type tp1 = compoundType(parts1, members2);
                    Type tp1 = (tp.symbol().isCompoundSym()) ? compoundTypeWithOwner(tp.symbol().owner(), parts1, members2)
                        : compoundType(parts1, members2, tp.symbol());
                    Symbol[] syms1 = members1.elements();
                    Symbol[] syms2 = new Symbol[syms1.length];
                    for (int i = 0; i < syms2.length; i++) {
                        syms2[i] = syms1[i].cloneSymbol(tp1.symbol());
                    }
                    for (int i = 0; i < syms2.length; i++) {
                        syms2[i].setInfo(syms1[i].info().subst(syms1, syms2));
                        if (syms2[i].kind == TYPE) {
                            syms2[i].setLoBound(syms1[i].loBound().subst(syms1, syms2));
                            syms2[i].setVuBound(syms1[i].vuBound().subst(syms1, syms2));
			}
                    }
                    for (int i = 0; i < syms2.length; i++) {
                        members2.enter(syms2[i]);
                    }
                    return tp1;
                }
            } else if (tp instanceof MethodType) {
                MethodType methodType = (MethodType)tp;
                Symbol[] vparams1 = map(methodType.vparams);
                Type result1 = apply(methodType.result);
                if (vparams1 == methodType.vparams && result1 == methodType.result) return tp;
                else return MethodType(vparams1, result1);
            } else if (tp instanceof PolyType) {
                PolyType polyType = (PolyType)tp;
                Symbol[] tparams1 = map(polyType.tparams);
                Type result1 = apply(polyType.result);
                if (tparams1 != polyType.tparams) result1 = result1.subst(polyType.tparams, tparams1);
                if (tparams1 == polyType.tparams && result1 == polyType.result) return tp;
                else return PolyType(tparams1, result1);
            } else if (tp instanceof OverloadedType) {
                OverloadedType overloadedType = (OverloadedType)tp;
                Type[] alttypes1 = map(overloadedType.alttypes);
                if (alttypes1 == overloadedType.alttypes) return tp;
                else return OverloadedType(overloadedType.alts, alttypes1);
            } else if (tp instanceof UnboxedArrayType) {
                UnboxedArrayType unboxedArrayType = (UnboxedArrayType)tp;
                Type elemtp1 = apply(unboxedArrayType.elemtp);
                if (elemtp1 == unboxedArrayType.elemtp) return tp;
                else return UnboxedArrayType(elemtp1);
            }
            throw new ApplicationError(tp + " " + tp.symbol());
        }

        public final Symbol map(Symbol sym) {
            return map(sym, false);
        }
        public Symbol map(Symbol sym, boolean dontClone) {
            Type tp = sym.info();
            Type tp1 = apply(tp);
            if (tp != tp1) {
                if (!dontClone) sym = sym.cloneSymbol();
                sym.setInfo(tp1);
                dontClone = true;
            }
            if (sym.kind == TYPE) {
                Type lb = sym.loBound();
                Type lb1 = apply(lb);
                if (lb != lb1) {
                    if (!dontClone) sym = sym.cloneSymbol();
                    sym.setLoBound(lb1);
                }
                Type vb = sym.vuBound();
                Type vb1 = apply(vb);
                if (vb != vb1) {
                    if (!dontClone) sym = sym.cloneSymbol();
                    sym.setVuBound(vb1);
                }
            }
            return sym;
        }

        public Type[] map(Type[] tps) {
            Type[] tps1 = tps;
            for (int i = 0; i < tps.length; i++) {
                Type tp = tps[i];
                Type tp1 = apply(tp);
                if (tp1 != tp && tps1 == tps) {
                    tps1 = new Type[tps.length];
                    System.arraycopy(tps, 0, tps1, 0, i);
                }
                tps1[i] = tp1;
            }
            return tps1;
        }

        /** Apply map to all elements of this array of symbols,
         *  preserving recursive references to symbols in the array.
         */
        public final Symbol[] map(Symbol[] syms) {
            return map(syms, false);
        }
        public Symbol[] map(Symbol[] syms, boolean dontClone) {
            Symbol[] syms1 = syms;
            for (int i = 0; i < syms.length; i++) {
                Symbol sym = syms[i];
                Symbol sym1 = map(sym, dontClone);
                if (sym != sym1 && syms1 == syms) {
                    syms1 = new Symbol[syms.length];
                    System.arraycopy(syms, 0, syms1, 0, i);
                }
                syms1[i] = sym1;
            }
            if (syms1 != syms) {
                for (int i = 0; i < syms1.length; i++) {
                    if (syms1[i] == syms[i])
                        syms1[i] = syms[i].cloneSymbol();
                }
                new SubstSymMap(syms, syms1).map(syms1, true);
            }
            return syms1;
        }

        /** Apply map to all elements of this array of this scope.
         */
        public Scope map(Scope s) {
            Symbol[] members = s.elements();
            Symbol[] members1 = map(members);
            if (members == members1) return s;
            else return new Scope(members1);
        }
    }

    public abstract static class MapOnlyTypes extends Map {
        public Symbol map(Symbol sym, boolean dontClone) { return sym; }
        public Symbol[] map(Symbol[] syms, boolean dontClone) { return syms; }
        public Scope map(Scope s) { return s; }
    }

    public static final Map IdMap = new Map() {
        public Type apply(Type tp) { return tp; }
        public Type applyParams(Type tp) { return tp; }
        public Type map(Type tp) { return tp; }
        public Symbol map(Symbol sym, boolean dontClone) { return sym; }
        public Type[] map(Type[] tps) { return tps; }
        public Symbol[] map(Symbol[] syms, boolean dontClone) { return syms; }
        public Scope map(Scope scope) { return scope; }
    };

// baseType and asSeenFrom --------------------------------------------------------

    /** Return the base type of this type whose symbol is `clazz', or NoType, if
     *  such a type does not exist.
     */
    public Type baseType(Symbol clazz) {
        //System.out.println(this + ".baseType(" + clazz + ")");//DEBUG
        if (this == ErrorType) {
            return ErrorType;
        } else if (this instanceof ThisType
                   || this instanceof SingleType
                   || this instanceof ConstantType) {
            return singleDeref().baseType(clazz);
        } else if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            if (typeRef.sym == clazz)
                return this;
            else if (typeRef.sym.kind == TYPE)
                return typeRef.sym.info()
                    .asSeenFrom(typeRef.pre, typeRef.sym.owner()).baseType(clazz);
            else if (typeRef.sym.kind == ALIAS)
                return Type.NoType;
            else if (clazz.isCompoundSym())
                return NoType;
            else {
                return typeRef.sym.baseType(clazz)
                    .asSeenFrom(typeRef.pre, typeRef.sym.owner())
                    .subst(typeRef.sym.typeParams(), typeRef.args);
            }
        } else if (this instanceof CompoundType) {
            Type[] parts = ((CompoundType)this).parts;
            for (int i = parts.length - 1; i >= 0; i--) {
                Type result = parts[i].baseType(clazz);
                if (result != NoType) return result;
            }
        } else if (this instanceof UnboxedArrayType) {
            if (clazz == Global.instance.definitions.ANY_CLASS ||
                clazz == Global.instance.definitions.ANYREF_CLASS)
                return clazz.type();
        }
        return NoType;
    }

    /** Return overriding instance of `sym' in this type,
     *  or `sym' itself if none exists.
     */
    public Symbol rebind(Symbol sym) {
        if (sym.kind != CLASS && (sym.flags & (PRIVATE | MODUL)) == 0) {
            Symbol sym1 = lookupNonPrivate(sym.name);
            if (sym1.kind != NONE) {
                if ((sym1.flags & LOCKED) != 0)
                    throw new Type.Error("illegal cyclic reference involving " + sym1);
                return sym1;
            }
        }
        return sym;
    }

    /** A map to implement `asSeenFrom'.
     */
    static class AsSeenFromMap extends Map {

        private final Type pre;
        private final Symbol clazz;
        private final boolean local;

        AsSeenFromMap(Type pre, Symbol clazz) {
            this.pre = pre;
            this.clazz = clazz;
            Global global = Global.instance;
            this.local = global.PHASE.EXPLICITOUTER.id() < global.currentPhase.id;
        }

        public Type apply(Type type) {
            //System.out.println(type + " as seen from " + pre + "," + clazz);//DEBUG
            if (pre == NoType || clazz.kind != CLASS)
                return type;
            if (type instanceof ThisType) {
                return type.toPrefix(((ThisType)type).sym, pre, clazz);
            } else if (type instanceof TypeRef) {
                TypeRef typeRef0 = (TypeRef)type;
                Type prefix = typeRef0.pre;
                Symbol sym = typeRef0.sym;
                Type[] args = typeRef0.args;
                if (sym.kind == ALIAS && sym.typeParams().length == args.length) {
                    return apply(
                        sym.info().subst(sym.typeParams(), args)
                        .asSeenFrom(prefix, sym.owner()));
                } else if (sym.owner().isPrimaryConstructor()) {
                    assert sym.kind == TYPE;
                    return type.toInstance(sym, pre, clazz);
                } else {
                    Type prefix1 = apply(prefix);
                    Type[] args1 = map(args);
                    if (prefix1 == prefix && args1 == args) return type;
                    Symbol sym1 = prefix1.rebind(sym);
                    if (local && sym != sym1 && sym1.isClassType()) {
                        args1 = asSeenFrom(Symbol.type(sym1.owner().typeParams()), pre, sym1.owner());
                        Type p = prefix1;
                        Symbol s = sym1.owner();
                        while (true) {
                            if (s.isPackage()) break;
                            if (s.isModuleClass()) {
                                s = s.owner();
                                p = p.prefix().baseType(s);
                            } else {
                                args1 = cloneArray(args1, 1);
                                args1[args1.length - 1] = p;
                                break;
                            }
                        }
                        if (sym1.isClassType()) prefix1 = localThisType;
                    }
                    Type type1 = typeRef(prefix1, sym1, args1);
                    if (sym1 != sym) type1 = apply(type1.unalias());
                    return type1;
                }
            } else if (type instanceof SingleType) {
                SingleType singleType0 = (SingleType)type;
                Type prefix = singleType0.pre;
                Symbol sym = singleType0.sym;
                try {
                    Type prefix1 = apply(prefix);
                    if (prefix1 == prefix) return type;
                    else return singleType(prefix1, prefix1.rebind(sym));
                } catch (Type.Malformed ex) {}
                return apply(type.singleDeref());
            }
            return map(type);
        }
    }
    //where
        Type toInstance(Symbol sym, Type pre, Symbol clazz) {
            if (pre == NoType || clazz.kind != CLASS)
                return this;
            Symbol ownclass = sym.owner().constructorClass();
            if (ownclass == clazz &&
                pre.widen().symbol().isSubClass(ownclass)) {
                Type baseType = pre.baseType(ownclass).withDefaultArgs();
                if (baseType instanceof TypeRef) {
                    Symbol basesym = ((TypeRef)baseType).sym;
                    Type[] baseargs = ((TypeRef)baseType).args;
                    Symbol[] baseparams = basesym.typeParams();
                    for (int i = 0; i < baseparams.length; i++) {
                        if (sym == baseparams[i]) return baseargs[i];
                    }
                } else if (baseType == ErrorType) {
                    return ErrorType;
                }
                throw new ApplicationError(
                    this + " in " + ownclass + " cannot be instantiated from " + pre.widen()
                    );
            } else {
                return toInstance(sym, pre.baseType(clazz).prefix(), clazz.owner());
            }
        }

        Type toPrefix(Symbol sym, Type pre, Symbol clazz) {
            //System.out.println(this + ".toPrefix(" + sym + "," + pre + "," + clazz + ")");//DEBUG
            if (pre == NoType || clazz.kind != CLASS)
                return this;
            else if (sym.isSubClass(clazz) &&
                     pre.widen().symbol().isSubClass(sym))
                return pre;
            else
                return toPrefix(sym, pre.baseType(clazz).prefix(), clazz.owner());
        }

    /** This type as seen from prefix `pre' and class `clazz'. This means:
     *  Replace all thistypes of `clazz' or one of its subclasses by `pre'
     *  and instantiate all parameters by arguments of `pre'.
     *  Proceed analogously for thistypes referring to outer classes.
     */
    public Type asSeenFrom(Type pre, Symbol clazz) {
        //System.out.println("computing asseenfrom of " + this + " with " + pre + "," + clazz);//DEBUG
        return new AsSeenFromMap(pre, clazz).apply(this);
    }

    /** Types `these' as seen from prefix `pre' and class `clazz'.
     */
    public static Type[] asSeenFrom(Type[] these, Type pre, Symbol clazz) {
        return new AsSeenFromMap(pre, clazz).map(these);
    }

    /** The info of `sym', seen as a member of this type.
     */
    public Type memberInfo(Symbol sym) {
        return sym.info().asSeenFrom(this, sym.owner());
    }

    /** The type of `sym', seen as a member of this type.
     */
    public Type memberType(Symbol sym) {
        return sym.type().asSeenFrom(this, sym.owner());
    }

    /** The stabilized type of `sym', seen as a member of this type.
     */
    public Type memberStabilizedType(Symbol sym) {
        return sym.isStable() && this.isStable()
            ? Type.singleTypeMethod(this, sym)
            : this.memberType(sym);
    }

    /** The low bound of `sym', seen as a member of this type.
     */
    public Type memberLoBound(Symbol sym) {
        return sym.loBound().asSeenFrom(this, sym.owner());
    }

    /** The view bound of `sym', seen as a member of this type.
     */
    public Type memberVuBound(Symbol sym) {
        return sym.vuBound().asSeenFrom(this, sym.owner());
    }

// Substitutions ---------------------------------------------------------------

    /** A common map superclass for symbol/symbol and type/symbol substitutions.
     */
    public static abstract class SubstMap extends Map {
        protected Symbol[] from;

        SubstMap(Symbol[] from) {
            this.from = from;
        }

        public boolean matches(Symbol sym1, Symbol sym2) {
            return sym1 == sym2;
        }

        /** Produce replacement type
         *  @param i          The index in `from' of the symbol to be replaced.
         *  @param fromtp     The type referring to this symbol.
         */
        protected abstract Type replacement(int i, Type fromtp);

        /** Produce new substitution where some symbols are excluded.
         *  @param newfrom    The new array of from symbols (without excluded syms)
         *  @param excluded   The array of excluded sysmbols
         */
        protected abstract SubstMap exclude(Symbol[] newfrom, Symbol[] excluded);

        public Type apply(Type t) {
            if (t instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)t;
                if (typeRef.pre == NoPrefix) {
                    for (int i = 0; i < from.length; i++) {
                        if (matches(typeRef.sym, from[i])) return replacement(i, t);
                    }
                }
            } else if (t instanceof SingleType) {
                SingleType singleType = (SingleType)t;
                if (singleType.pre == NoPrefix) {
                    for (int i = 0; i < from.length; i++) {
                        if (matches(singleType.sym, from[i])) return replacement(i, t);
                    }
                }
            } else if (t instanceof PolyType) {
                PolyType polyType = (PolyType)t;
                Symbol[] from1 = excludeSyms(from, polyType.tparams, from);
                if (from1 != from) {
                    SubstMap f = exclude(from1, polyType.tparams);
                    Symbol[] tparams1 = f.map(polyType.tparams);
                    Type result1 = f.apply(polyType.result);
                    if (tparams1 != polyType.tparams)
                        result1 = result1.subst(polyType.tparams, tparams1);
                    if (tparams1 == polyType.tparams && result1 == polyType.result) return t;
                    else return PolyType(tparams1, result1);
                }
            }
            return map(t);
        }
        //where
        private boolean contains1(Symbol[] syms, Symbol sym) {
            int i = 0;
            while (i < syms.length && syms[i] != sym) i++;
            return i < syms.length;
        }

        private int nCommon(Symbol[] from, Symbol[] tparams) {
            int cnt = 0;
            for (int i = 0; i < from.length; i++) {
                if (contains1(tparams, from[i])) cnt++;
            }
            return cnt;
        }

        protected final Symbol[] excludeSyms(Symbol[] from, Symbol[] tparams, Symbol[] syms) {
            int n = nCommon(from, tparams);
            if (n == 0) {
                return syms;
            } else {
                Symbol[] syms1 = new Symbol[syms.length - n];
                int j = 0;
                for (int i = 0; i < from.length; i++) {
                    if (!contains1(tparams, from[i])) syms1[j++] = syms[i];
                }
                return syms1;
            }
        }

        protected final Type[] excludeTypes(Symbol[] from, Symbol[] tparams, Type[] types) {
            int n = nCommon(from, tparams);
            if (n == 0) {
                return types;
            } else {
                Type[] types1 = new Type[types.length - n];
                int j = 0;
                for (int i = 0; i < from.length; i++) {
                    if (!contains1(tparams, from[i])) types1[j++] = types[i];
                }
                return types1;
            }
        }
    }

    /** A map for symbol/symbol substitutions
     */
    public static class SubstSymMap extends SubstMap {
        Symbol[] to;
        protected SubstSymMap(Symbol[] from, Symbol[] to) {
            super(from);
            this.to = to;
        }
        protected Type replacement(int i, Type fromtp) {
            if (fromtp instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)fromtp;
                return typeRef(typeRef.pre, to[i], typeRef.args);
            } else if (fromtp instanceof SingleType) {
                SingleType singleType = (SingleType)fromtp;
                return singleType(singleType.pre, to[i]);
            }
            throw new ApplicationError();
        }
        protected SubstMap exclude(Symbol[] newfrom, Symbol[] excluded) {
            return new SubstSymMap(newfrom, excludeSyms(from, excluded, to));
        }
    }

    /** A map for type/symbol substitutions
     */
    public static class SubstTypeMap extends SubstMap {
        Type[] to;
        public SubstTypeMap(Symbol[] from, Type[] to) {
            super(from);
            this.to = to;
        }
        public Type replacement(int i, Type fromtp) {
            return to[i];
        }
        public SubstMap exclude(Symbol[] newfrom, Symbol[] excluded) {
            return new SubstTypeMap(newfrom, excludeTypes(from, excluded, to));
        }
    }


    /** A map for symbol/symbol substitutions which, instead of
     * cloning parameters, updates their symbol's types.
     */
    public static class UpdateSubstSymMap extends SubstSymMap {
        protected UpdateSubstSymMap(Symbol[] from, Symbol[] to) {
            super(from, to);
        }
        public Type apply(Type t) {
            if (t instanceof PolyType) {
                PolyType polyType = (PolyType)t;
                // !!! Also update loBounds? How? loBound can only be set!
                for (int i = 0; i < polyType.tparams.length; i++) {
                    Type tp = polyType.tparams[i].nextType();
                    Type tp1 = apply(tp);
                    if (tp != tp1) polyType.tparams[i].updateInfo(tp1);
                }
                Type result1 = apply(polyType.result);
                if (result1 == polyType.result) return t;
                else return Type.PolyType(polyType.tparams, result1);
            } else if (t instanceof MethodType) {
                MethodType methodType = (MethodType)t;
                for (int i = 0; i < methodType.vparams.length; i++) {
                    Type tp = methodType.vparams[i].nextType();
                    Type tp1 = apply(tp);
                    if (tp != tp1) methodType.vparams[i].updateInfo(tp1);
                }
                Type result1 = apply(methodType.result);
                if (result1 == methodType.result) return t;
                else return Type.MethodType(methodType.vparams, result1);
            }
            return super.apply(t);
        }
        public Symbol map(Symbol sym, boolean dontClone) { return sym; }
        public Symbol[] map(Symbol[] syms, boolean dontClone) { return syms; }
        public Scope map(Scope s) { return s; }
    }

    /** Returns the given non-updating symbol/symbol substitution. */
    public static Map getSubst(Symbol[] from, Symbol[] to) {
        return getSubst(from, to, false);
    }

    /** Returns the given (updating?) symbol/symbol substitution. */
    public static Map getSubst(Symbol[] from, Symbol[] to, boolean update) {
        if (from.length == 0 && to.length == 0) return IdMap;
        if (update) return new UpdateSubstSymMap(from, to);
        return new SubstSymMap(from, to);
    }

    /** Returns the given non-updating symbol/type substitution. */
    public static Map getSubst(Symbol[] from, Type[] to) {
        if (from.length == 0 && to.length == 0) return IdMap;
        return new SubstTypeMap(from, to);
    }

    /** Substitute symbols `to' for occurrences of symbols `from' in this type.
     */
    public Type subst(Symbol[] from, Symbol[] to) {
        if (to.length != 0 && from != to) {//!!!
            assert from.length == to.length
                : this + ": " + from.length + " != " + to.length;
            return new SubstSymMap(from, to).apply(this);
        } else return this;
    }

    /** Substitute symbols `to' for occurrences of symbols `from' in these types.
     */
    public static Type[] subst(Type[] these, Symbol[] from, Symbol[] to) {
        if (these.length != 0 && to.length != 0 && from != to) {
            assert from.length == to.length;
            return new SubstSymMap(from, to).map(these);
        } else return these;
    }

    /** Substitute types `to' for occurrences of symbols `from' in this type.
     */
    public Type subst(Symbol[] from, Type[] to) {
        if (to.length != 0) {
            assert from.length == to.length
                : this + ": " + Debug.show(from) + " <> " + ArrayApply.toString(to);
            return new SubstTypeMap(from, to).apply(this);
        } else return this;
    }

    /** Substitute types `to' for occurrences of symbols `from' in these types.
     */
    public static Type[] subst(Type[] these, Symbol[] from, Type[] to) {
        if (these.length != 0 && to.length != 0) {
            assert from.length == to.length;
            return new SubstTypeMap(from, to).map(these);
        } else return these;
    }

    /**
     * A map that substitutes ThisTypes of a given class by a given
     * type. All occurrences of the type parameters of the given class
     * are replaced by the type arguments extracted from the given
     * type. Furthermore, the prefixes of the given type are used to
     * substitute, in the same way, the ThisTypes of the outer classes
     * of the given class.
     *
     * object Foo {
     *   class C[D] { class I[J]; }
     *   val c: C[Int] = new C[Int];
     *   class M[N] extends c.I[N];
     * }
     *
     * In the code above, a ThisTypeMap of class "I" and type
     * "ThisType(M)", would do the following substitutions:
     *
     *   - ThisType(I)       ->  ThisType(M)
     *   - TypeRef(_, J, _)  ->  TypeRef(_, N, -)
     *   - ThisType(C)       ->  SingleType(ThisType(Foo), c)
     *   - TypeRef(_, D, _)  ->  TypeRef(_, Int, _)
     */
    private static class ThisTypeMap extends Map {

        private static Map create(Symbol clasz, Type type) {
            HashMap subst = getSubst(clasz, type, 0);
            return subst == null ? IdMap : new ThisTypeMap(subst);
        }

        private static HashMap getSubst(Symbol clasz, Type type, int capacity){
            if (type == NoPrefix) {
                return getSubst(capacity);
            } else if (type instanceof ThisType) {
                Symbol symbol = ((ThisType)type).sym;
                if (symbol == clasz) return getSubst(capacity);
                if (symbol.isNone()) return getSubst(capacity);
            }
            Type base = type.baseType(clasz);
            if (base instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)base;
                Type prefix = typeRef.pre;
                Symbol symbol = typeRef.sym;
                Type[] args = typeRef.args;
                capacity += 1 + args.length;
                HashMap subst = getSubst(clasz.owner(), prefix, capacity);
                subst.put(clasz, type);
                Symbol[] params = clasz.typeParams();
                assert symbol == clasz && args.length == params.length:
                    type + " @ " + Debug.show(clasz) + " -> " + base;
                for (int i = 0; i < params.length; i++) {
                    assert params[i].isParameter(): Debug.show(params[i]);
                    subst.put(params[i], args[i]);
                }
                return subst;
            }
            throw Debug.abort("illegal case",
                type + " @ " + Debug.show(clasz) + " -> " + base);
        }

        private static HashMap getSubst(int capacity) {
            return capacity == 0 ? null : new HashMap(capacity);
        }

        private final HashMap/*<Symbol,Type>*/ subst;

        private ThisTypeMap(HashMap subst) {
            this.subst = subst;
        }

        public Type apply(Type type) {
            if (type instanceof ThisType) {
                Symbol symbol = ((ThisType)type).sym;
                Object lookup = subst.get(symbol);
                if (lookup != null) return (Type)lookup;
            } else if (type instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)type;
                if (!typeRef.sym.isParameter())
                    return map(type);
                assert typeRef.args.length == 0: type;
                Object lookup = subst.get(typeRef.sym);
                if (lookup != null) return (Type)lookup;
            }
            return map(type);
        }

        public String toString() {
            return subst.toString();
        }

    }

    /** Returns a ThisTypeMap of given class and type. */
    public static Map getThisTypeMap(Symbol clasz, Type type) {
        return ThisTypeMap.create(clasz, type);
    }

    /** A map for substitutions of thistypes.
     */
    public static class SubstThisMap extends Map {
        Symbol from;
        Type to;
        public SubstThisMap(Symbol from, Type to) {
            this.from = from;
            this.to = to;
        }
        public SubstThisMap(Symbol oldSym, Symbol newSym) {
            this(oldSym, newSym.thisType());
        }
        public Type apply(Type type) {
            if (type instanceof ThisType) {
                return ((ThisType)type).sym == from ? to : type;
            }
            return map(type);
        }
    }

    public Type substThis(Symbol from, Type to) {
        return new SubstThisMap(from, to).apply(this);
    }

    public static Type[] substThis(Type[] these, Symbol from, Type to) {
        return new SubstThisMap(from, to).map(these);
    }

    static class ContainsMap extends Map {
        boolean result = false;
        Symbol sym;
        ContainsMap(Symbol sym) {
            this.sym = sym;
        }
        public Type apply(Type t) {
            if (!result) {
                if (t instanceof TypeRef) {
                    TypeRef typeRef = (TypeRef)t;
                    if (sym == typeRef.sym) result = true;
                    else {
                        map(typeRef.pre);
                        map(typeRef.args);
                    }
                } else if (t instanceof SingleType) {
                    SingleType singleType = (SingleType)t;
                    map(singleType.pre);
                    if (sym == singleType.sym) result = true;
                } else {
                    map(t);
                }
            }
            return t;
        }
    }

    /** Does this type contain symbol `sym'?
     */
    public boolean contains(Symbol sym) {
        ContainsMap f = new ContainsMap(sym);
        f.apply(this);
        return f.result;
    }

    /** Does this type contain any of the symbols `syms'?
     */
    public boolean containsSome(Symbol[] syms) {
        for (int i = 0; i < syms.length; i++)
            if (contains(syms[i])) return true;
        return false;
    }

// Cloning ---------------------------------------------------------------

    /** Returns a shallow copy of the given array. */
    public static Type[] cloneArray(Type[] array) {
        return cloneArray(0, array, 0);
    }

    /**
     * Returns a shallow copy of the given array prefixed by "prefix"
     * null items.
     */
    public static Type[] cloneArray(int prefix, Type[] array) {
        return cloneArray(prefix, array, 0);
    }

    /**
     * Returns a shallow copy of the given array suffixed by "suffix"
     * null items.
     */
    public static Type[] cloneArray(Type[] array, int suffix) {
        return cloneArray(0, array, suffix);
    }

    /**
     * Returns a shallow copy of the given array prefixed by "prefix"
     * null items and suffixed by "suffix" null items.
     */
    public static Type[] cloneArray(int prefix, Type[] array, int suffix) {
        assert prefix >= 0 && suffix >= 0: prefix + " - " + suffix;
        int size = prefix + array.length + suffix;
        if (size == 0) return EMPTY_ARRAY;
        Type[] clone = new Type[size];
        for (int i = 0; i < array.length; i++) clone[prefix + i] = array[i];
        return clone;
    }

    /** Returns the concatenation of the two arrays. */
    public static Type[] concat(Type[] array1, Type[] array2) {
        if (array1.length == 0) return array2;
        if (array2.length == 0) return array1;
        Type[] clone = cloneArray(array1.length, array2);
        for (int i = 0; i < array1.length; i++) clone[i] = array1[i];
        return clone;
    }

    /**
     * Clones a type i.e. returns a new type where all symbols in
     * MethodTypes and PolyTypes and CompoundTypes have been cloned.
     */
    public Type cloneType(Symbol oldOwner, Symbol newOwner) {
        SymbolCloner cloner = new SymbolCloner();
        cloner.owners.put(oldOwner, newOwner);
        return cloner.cloneType(this);
    }

    /**
     * Clones a type i.e. returns a new type where all symbols in
     * MethodTypes and PolyTypes have been cloned. This method
     * performs no substitution on the type of the cloned symbols.
     * Typically, the type of those symbols will be fixed later by
     * applying some Map.applyParams method to the returned type.
     */
    public Type cloneTypeNoSubst(SymbolCloner cloner) {
        if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
            Symbol[] clones = cloner.cloneSymbols(methodType.vparams);
            return Type.MethodType(clones, methodType.result.cloneTypeNoSubst(cloner));
        } else if (this instanceof PolyType) {
            PolyType polyType = (PolyType)this;
            Symbol[] clones = cloner.cloneSymbols(polyType.tparams);
            return Type.PolyType(clones, polyType.result.cloneTypeNoSubst(cloner));
        }
        return this;
    }


// Comparisons ------------------------------------------------------------------

    /** Type relations */
    public enum Relation {
        SubType,   // this SubType   that <=> this.isSubType(that)
        SameType,  // this SameType  that <=> this.isSameAs(that)
        SuperType; // this SuperType that <=> that.isSubType(this)

        public String toString() {
            return toString(false);
        }
        public String toString(boolean negate) {
            switch (this) {
            case SubType  : return negate ? "!<=" : "<=";
            case SameType : return negate ? "!==" : "=";
            case SuperType: return negate ? "!>=" : ">=";
            default       : throw Debug.abort("unknown relation", this);
            }
        }
    }

    /** Is this type in given relation to that type?
     */
    public boolean compareTo(Type that, Relation relation) {
        switch (relation) {
        case SubType  : return this.isSubType(that);
        case SameType : return this.isSameAs(that);
        case SuperType: return that.isSubType(this);
        default       : throw Debug.abort("unknown relation", relation);
        }
    }

    /** Is this type a subtype of that type?
     */
    public boolean isSubType(Type that) {
        if (explainSwitch) {
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(this + " < " + that + "?");
            indent++;
        }
        boolean result = isSubType0(that);
        if (explainSwitch) {
            indent--;
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(result);
        }
        return result;
    }

    public boolean isSubType0(Type that) {
        if (this == that) return true;

        if (this == ErrorType || this == AnyType) {
            return true;
        }

        if (that == ErrorType || that == AnyType) {
            return true;
        } else if (that == NoType || that == NoPrefix) {
            return false;
        } else if (that instanceof ThisType
                   || that instanceof SingleType
                   ) {
            if (this instanceof ThisType
                || this instanceof SingleType) {
                return this.isSameAs(that);
            }
        } else if (that instanceof ConstantType) {
            if (this instanceof ConstantType) {
                ConstantType thisConstantType = (ConstantType)this;
                return this.isSameAs(that) || thisConstantType.base.isSubType(that);
            }
        } else if (that instanceof TypeRef) {
            TypeRef thatTypeRef = (TypeRef)that;
            if (this instanceof TypeRef) {
                TypeRef thisTypeRef = (TypeRef)this;
                if ((thisTypeRef.pre.isSubType(thatTypeRef.pre) &&
                     thisTypeRef.sym == thatTypeRef.sym &&
                     isSubArgs(thisTypeRef.args, thatTypeRef.args, thisTypeRef.sym.typeParams()))
                    ||
                    (thisTypeRef.sym.kind == TYPE && thisTypeRef.pre.memberInfo(thisTypeRef.sym).isSubType(that)))
                    return true;
            }
            if (thatTypeRef.sym.kind == CLASS) {
                Type base = this.baseType(thatTypeRef.sym);
                if (this != base && base.isSubType(that))
                    return true;
            }
        } else if (that instanceof CompoundType) {
            CompoundType compoundType = (CompoundType)that;
            int i = 0;
            while (i < compoundType.parts.length && isSubType(compoundType.parts[i])) i++;
            if (i == compoundType.parts.length && specializes(compoundType.members))
                return true;
        } else if (that instanceof MethodType) {
            if (this instanceof MethodType) {
                MethodType thisMethodType = (MethodType)this;
                MethodType thatMethodType = (MethodType)that;
                if (thisMethodType.vparams.length != thatMethodType.vparams.length) return false;
                for (int i = 0; i < thisMethodType.vparams.length; i++) {
                    Symbol p1 = thatMethodType.vparams[i];
                    Symbol p = thisMethodType.vparams[i];
                    if (!p1.type().isSameAs(p.type()) ||
                        (p1.flags & (DEF | REPEATED)) != (p.flags & (DEF | REPEATED)))
                        return false;
                }
                return thisMethodType.result.isSubType(thatMethodType.result);
            }
        } else if (that instanceof PolyType) {
            if (this instanceof PolyType) {
                PolyType thisPolyType = (PolyType)this;
                PolyType thatPolyType = (PolyType)that;
                if (thisPolyType.tparams.length != thatPolyType.tparams.length) return false;
                for (int i = 0; i < thisPolyType.tparams.length; i++)
                    if (!thatPolyType.tparams[i].info().subst(thatPolyType.tparams, thisPolyType.tparams).isSubType(thisPolyType.tparams[i].info()) ||
                        !thisPolyType.tparams[i].loBound().isSubType(thatPolyType.tparams[i].loBound().subst(thatPolyType.tparams, thisPolyType.tparams)) ||
                        !thatPolyType.tparams[i].vuBound().subst(thatPolyType.tparams, thisPolyType.tparams).isSubType(thisPolyType.tparams[i].vuBound()))
                        return false;
                return thisPolyType.result.isSubType(thatPolyType.result.subst(thatPolyType.tparams, thisPolyType.tparams));
            }
        } else if (that instanceof OverloadedType) {
            OverloadedType overloadedType = (OverloadedType)that;
            for (int i = 0; i < overloadedType.alttypes.length; i++) {
                if (!isSubType(overloadedType.alttypes[i]))
                    return false;
            }
            return true;
        } else if (that instanceof UnboxedType) {
            if (this instanceof UnboxedType) {
                return ((UnboxedType)this).tag == ((UnboxedType)that).tag;
            }
        } else if (that instanceof UnboxedArrayType) {
            if (this instanceof UnboxedArrayType) {
                return ((UnboxedArrayType)this).elemtp.isSubType(((UnboxedArrayType)that).elemtp);
            }
        } else if (that instanceof TypeVar) {
            TypeVar typeVar = (TypeVar)that;
            //todo: should we test for equality with origin?
            if (typeVar.constr.inst != NoType) {
                return this.isSubType(typeVar.constr.inst);
            } else {
                typeVar.constr.lobounds = new List(this, typeVar.constr.lobounds);
                return true;
            }
        } else {
            throw new ApplicationError(this + " <: " + that);
        }

        if (this == NoType || this == NoPrefix) {
            return false;
        } else if (this instanceof ThisType || this instanceof SingleType) {
            if (this.singleDeref().isSubType(that)) return true;
        } else if (this instanceof ConstantType) {
            if (this.singleDeref().isSubType(that)) return true;
        } else if (this instanceof TypeVar) {
            TypeVar typeVar = (TypeVar)this;
            if (typeVar.constr.inst != NoType) {
                return typeVar.constr.inst.isSubType(that);
            } else {
                typeVar.constr.hibounds = new List(that, typeVar.constr.hibounds);
                return true;
            }
        } else if (this instanceof TypeRef) {
            TypeRef thisTypeRef = (TypeRef)this;
            if (that instanceof TypeRef) {
                TypeRef thatTypeRef = (TypeRef)that;
                if (thatTypeRef.sym.kind == TYPE && this.isSubType(that.loBound()))
                    return true;
            }
            if (thisTypeRef.sym.kind == ALIAS && thisTypeRef.sym.typeParams().length == thisTypeRef.args.length)
                return this.unalias().isSubType(that);
            else if (thisTypeRef.sym == Global.instance.definitions.ALL_CLASS)
                return that.isSubType(Global.instance.definitions.ANY_TYPE());
            else if (thisTypeRef.sym == Global.instance.definitions.ALLREF_CLASS)
                return
                    that.symbol() == Global.instance.definitions.ANY_CLASS ||
                    (that.symbol() != Global.instance.definitions.ALL_CLASS &&
                     that.isSubType(Global.instance.definitions.ANYREF_TYPE()));
        } else if (this instanceof OverloadedType) {
            OverloadedType overloadedType = (OverloadedType)this;
            for (int i = 0; i < overloadedType.alttypes.length; i++) {
                if (overloadedType.alttypes[i].isSubType(that)) return true;
            }
        } else if (this instanceof CompoundType) {
            CompoundType compoundType = (CompoundType)this;
            int i = 0;
            while (i < compoundType.parts.length) {
                if (compoundType.parts[i].isSubType(that)) return true;
                i++;
            }
        } else if (this instanceof UnboxedArrayType) {
            if (Global.instance.definitions.OBJECT_TYPE().isSubType(that))
                return true;
            // !!! we should probably also test for Clonable, Serializable, ...
        }

        if (that instanceof TypeRef) {
            TypeRef thatTypeRef = (TypeRef)that;
            if (thatTypeRef.sym.kind == ALIAS && thatTypeRef.sym.typeParams().length == thatTypeRef.args.length)
                return this.isSubType(that.unalias());
        }

        return false;
    }

    /** Are types `these' subtypes of corresponding types `those'?
     */
    public static boolean isSubType(Type[] these, Type[] those) {
        if (these.length != those.length) return false;
        for (int i = 0; i < these.length; i++) {
            if (!these[i].isSubType(those[i])) return false;
        }
        return true;
    }

    /** Are types `these' arguments types conforming to corresponding types `those'?
     */
    static boolean isSubArgs(Type[] these, Type[] those, Symbol[] tparams) {
        if (these.length != those.length) return false;
        for (int i = 0; i < these.length; i++) {
            if ((tparams[i].flags & COVARIANT) != 0) {
                if (!these[i].isSubType(those[i])) return false;
            } else if ((tparams[i].flags & CONTRAVARIANT) != 0) {
                //System.out.println("contra: " + these[i] + " " + those[i] + " " + those[i].isSubType(these[i]));//DEBUG
                if (!those[i].isSubType(these[i])) return false;
            } else {
                if (!these[i].isSameAs(those[i])) return false;
            }
        }
        return true;
    }

    public static boolean isSubSet(Type[] alts, Type[] alts1) {
        for (int i = 0; i < alts.length; i++) {
            int j = 0;
            while (j < alts1.length && !alts1[j].isSameAs(alts[i])) j++;
            if (j == alts1.length) return false;
        }
        return true;
    }

    /** Does this type implement all symbols in scope `s' with same or stronger types?
     */
    public boolean specializes(Scope s) {
        for (Scope.SymbolIterator it = s.iterator(true); it.hasNext();) {
            if (!specializes(it.next())) return false;
        }
        return true;
    }

    /** Does this type implement symbol `sym1' with same or stronger type?
     */
    public boolean specializes(Symbol sym1) {
        if (explainSwitch) {
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(this + " specializes " + sym1 + "?");
            indent++;
        }
        boolean result = specializes0(sym1);
        if (explainSwitch) {
            indent--;
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(result);
        }
        return result;
    }

    private boolean specializes0(Symbol sym1) {
        Type self = narrow();
        Symbol[] tparams = symbol().typeParams();
        Type[] targs = typeArgs();
        Symbol sym = lookup(sym1.name);
        return
            sym.kind != NONE &&
            (sym == sym1
             ||
             (sym.kind == sym1.kind || sym1.kind == TYPE) &&
             self.memberInfo(sym).subst(tparams, targs)
             .isSubType(sym1.info().substThis(sym1.owner(), self)) &&
             sym1.loBound().substThis(sym1.owner(), self)
             .isSubType(self.memberLoBound(sym).subst(tparams, targs)) &&
             self.memberVuBound(sym).subst(tparams, targs)
             .isSubType(sym1.vuBound().substThis(sym1.owner(), self))
             ||
             (sym.kind == TYPE && sym1.kind == ALIAS &&
              sym1.info().unalias().isSameAs(sym.type())));
    }

    /** Is this type the same as that type?
     */
    public boolean isSameAs(Type that) {
        if (explainSwitch) {
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(this + " = " + that + "?");
            indent++;
        }
        boolean result = isSameAs0(that);
        if (explainSwitch) {
            indent--;
            for (int i = 0; i < indent; i++) System.out.print("  ");
            System.out.println(result);
        }
        return result;
    }

    public boolean isSameAs0(Type that) {
        if (this == that) return true;

        if (this == ErrorType || this == AnyType) {
            return true;
        } else if (this instanceof ThisType) {
            Symbol sym = ((ThisType)this).sym;
            if (that instanceof ThisType) {
                return sym == ((ThisType)that).sym;
            } else if (that instanceof SingleType) {
                SingleType thatSingleType = (SingleType)that;
                return (thatSingleType.sym.isModule()
                    && sym == thatSingleType.sym.moduleClass()
                    && sym.owner().thisType().isSameAs(thatSingleType.pre))
                    ||
                    deAlias(that) != that &&
                    this.isSameAs(deAlias(that));
            }
        } else if (this instanceof SingleType) {
            SingleType thisSingleType = (SingleType)this;
            if (that instanceof SingleType) {
                SingleType thatSingleType = (SingleType)that;
                return (thisSingleType.sym == thatSingleType.sym && thisSingleType.pre.isSameAs(thatSingleType.pre))
                    ||
                    (deAlias(this) != this || deAlias(that) != that) &&
                    deAlias(this).isSameAs(deAlias(that));
            } else if (that instanceof ThisType) {
                Symbol sym1 = ((ThisType)that).sym;
                return (thisSingleType.sym.isModule()
                    && thisSingleType.sym.moduleClass() == sym1
                    && thisSingleType.pre.isSameAs(sym1.owner().thisType()))
                    ||
                    deAlias(this) != this &&
                    deAlias(this).isSameAs(that);
            } else {
                if (deAlias(this) != this)
                    return deAlias(this).isSameAs(that);
            }
        } else if (this instanceof ConstantType) {
            ConstantType thisConstantType = (ConstantType)this;
            if (that instanceof ConstantType) {
                ConstantType thatConstantType = (ConstantType)that;
                return thisConstantType.base.isSameAs(thatConstantType.base)
                    && thisConstantType.value.equals(thatConstantType.value);
            }
        } else if (this instanceof TypeRef) {
            TypeRef thisTypeRef = (TypeRef)this;
            if (that instanceof TypeRef) {
                TypeRef thatTypeRef = (TypeRef)that;
                if (thisTypeRef.sym == thatTypeRef.sym
                    && thisTypeRef.pre.isSameAs(thatTypeRef.pre)
                    && isSameAs(thisTypeRef.args, thatTypeRef.args))
                    return true;
            }
        } else if (this instanceof CompoundType) {
            CompoundType thisCompoundType = (CompoundType)this;
            if (that instanceof CompoundType) {
                CompoundType thatCompoundType = (CompoundType)that;
                if (thisCompoundType.parts.length != thatCompoundType.parts.length) return false;
                for (int i = 0; i < thisCompoundType.parts.length; i++)
                    if (!thisCompoundType.parts[i].isSameAs(thatCompoundType.parts[i])) return false;
                return isSameAs(thisCompoundType.members, thatCompoundType.members);
            }
        } else if (this instanceof MethodType) {
            MethodType thisMethodType = (MethodType)this;
            if (that instanceof MethodType) {
                MethodType thatMethodType = (MethodType)that;
                if (thisMethodType.vparams.length != thatMethodType.vparams.length) return false;
                for (int i = 0; i < thisMethodType.vparams.length; i++) {
                    Symbol p1 = thatMethodType.vparams[i];
                    Symbol p = thisMethodType.vparams[i];
                    if (!p1.type().isSameAs(p.type()) ||
                        (p1.flags & (DEF | REPEATED)) != (p.flags & (DEF | REPEATED)))
                        return false;
                }
                return thisMethodType.result.isSameAs(thatMethodType.result);
            }
        } else if (this instanceof PolyType) {
            PolyType thisPolyType = (PolyType)this;
            if (that instanceof PolyType) {
                PolyType thatPolyType = (PolyType)that;
                if (thisPolyType.tparams.length != thatPolyType.tparams.length) return false;
                for (int i = 0; i < thisPolyType.tparams.length; i++)
                    if (!thatPolyType.tparams[i].info().subst(thatPolyType.tparams, thisPolyType.tparams).isSameAs(thisPolyType.tparams[i].info()) ||
                        !thatPolyType.tparams[i].loBound().subst(thatPolyType.tparams, thisPolyType.tparams).isSameAs(thisPolyType.tparams[i].loBound()) ||
                        !thatPolyType.tparams[i].vuBound().subst(thatPolyType.tparams, thisPolyType.tparams).isSameAs(thisPolyType.tparams[i].vuBound()))
                        return false;
                return thisPolyType.result.isSameAs(thatPolyType.result.subst(thatPolyType.tparams, thisPolyType.tparams));
            }
        } else if (this instanceof OverloadedType) {
            if (that instanceof OverloadedType) {
                OverloadedType thisOverloadedType = (OverloadedType)this;
                OverloadedType thatOverloadedType = (OverloadedType)that;
                return isSubSet(thatOverloadedType.alttypes, thisOverloadedType.alttypes)
                    && isSubSet(thisOverloadedType.alttypes, thatOverloadedType.alttypes);
            }
        } else if (this instanceof UnboxedType) {
            if (that instanceof UnboxedType) {
                return ((UnboxedType)this).tag == ((UnboxedType)that).tag;
            }
        } else if (this instanceof UnboxedArrayType) {
            if (that instanceof UnboxedArrayType) {
                return ((UnboxedArrayType)this).elemtp.isSameAs(((UnboxedArrayType)that).elemtp);
            }
        }

        if (that == ErrorType || that == AnyType) {
            return true;
        } else if (that == NoType || that == NoPrefix) {
            return false;
        } else if (that instanceof TypeVar) {
            Constraint constr = ((TypeVar)that).constr;
            if (constr.inst != NoType) return constr.inst.isSameAs(this);
            else return constr.instantiate(this.any2typevar());
        }

        if (this == NoType || this == NoPrefix) {
            return false;
        } else if (this instanceof TypeRef) {
            TypeRef thisTypeRef = (TypeRef)this;
            if (thisTypeRef.sym.kind == ALIAS && thisTypeRef.sym.typeParams().length == thisTypeRef.args.length)
                return this.unalias().isSameAs(that);
        } else if (this instanceof TypeVar) {
            Constraint constr = ((TypeVar)this).constr;
            if (constr.inst != NoType) return constr.inst.isSameAs(that);
            else return constr.instantiate(that.any2typevar());
        }

        if (that instanceof TypeRef) {
            TypeRef thatTypeRef = (TypeRef)that;
            if (thatTypeRef.sym.kind == ALIAS && thatTypeRef.sym.typeParams().length == thatTypeRef.args.length)
                return this.isSameAs(that.unalias());
        }
        return false;
    }
    //where
        Type deAlias(Type tp) {
            if (tp instanceof SingleType) {
                Type tp1 = tp.singleDeref();
                if (tp1.isStable()) return deAlias(tp1);
            }
            return tp;
        }

    /** Are types `these' the same as corresponding types `those'?
     */
    public static boolean isSameAs(Type[] these, Type[] those) {
        if (these.length != those.length) return false;
        for (int i = 0; i < these.length; i++) {
            if (!these[i].isSameAs(those[i])) return false;
        }
        return true;
    }

    /** Do scopes `s1' and `s2' define he same symbols with the same kinds and infos?
     */
    public boolean isSameAs(Scope s1, Scope s2) {
        return isSubScope(s1, s2) && isSubScope(s2, s1);
    }

    /** Does scope `s1' define all symbols of scope `s2' with the same kinds and infos?
     */
    private boolean isSubScope(Scope s1, Scope s2) {
        for (Scope.SymbolIterator it = s2.iterator(); it.hasNext(); ) {
            Symbol sym2 = it.next();
            Symbol sym1 = s1.lookup(sym2.name);
            if (sym1.kind != sym2.kind ||
                !sym1.info().isSameAs(
                    sym2.info().substThis(
                        sym2.owner(), sym1.owner().thisType())) ||
                !sym1.loBound().isSameAs(
                    sym2.loBound().substThis(
                        sym2.owner(), sym1.owner().thisType())) ||
                !sym1.vuBound().isSameAs(
                    sym2.vuBound().substThis(
                        sym2.owner(), sym1.owner().thisType())))
                return false;
        }
        return true;
    }

    boolean isSameAsAll(Type[] tps) {
        int i = 1;
        while (i < tps.length && isSameAs(tps[i])) i++;
        return i == tps.length;
    }

    /** Map every occurrence of AnyType to a fresh type variable.
     */
    public static Map any2typevarMap = new Map() {
        public Type apply(Type t) { return t.any2typevar(); }
    };

    public Type any2typevar() {
        if (this == AnyType) {
            return TypeVar(this, new Constraint());
        }
        return any2typevarMap.map(this);
    }

    /** Does this type match type `tp', so that corresponding symbols with
     *  the two types would be taken to override each other?
     */
    public boolean overrides(Type tp) {
	if (this instanceof Type.OverloadedType) {
	    Type.OverloadedType overloadedType = (Type.OverloadedType) this;
	    Type[] alttypes = overloadedType.alttypes;
	    for (int i = 0; i < alttypes.length; i++) {
		if (alttypes[i].overrides(tp)) return true;
	    }
	    return false;
	} else {
	    if (tp instanceof Type.MethodType) {
		Type.MethodType methodType1 = (Type.MethodType) tp;
		Symbol[] ps1 = methodType1.vparams;
		Type res1 = methodType1.result;
		if (this instanceof Type.MethodType) {
		    Type.MethodType methodType = (Type.MethodType) this;
		    Symbol[] ps = methodType.vparams;
		    Type res = methodType.result;
		    if (ps.length != ps1.length) return false;
		    for (int i = 0; i < ps.length; i++) {
			Symbol p1 = ps1[i];
			Symbol p = ps[i];
			if (!p1.type().isSameAs(p.type()) ||
			    (p1.flags & (DEF | REPEATED)) != (p.flags & (DEF | REPEATED)))
			    return false;
		    }
		    return res.overrides(res1);
		}
		return false;
	    } else if (tp instanceof Type.PolyType) {
		Type.PolyType polyType1 = (Type.PolyType) tp;
		Symbol[] ps1 = polyType1.tparams;
		Type res1 = polyType1.result;
		if (this instanceof Type.PolyType) {
		    Type.PolyType polyType = (Type.PolyType) this;
		    Symbol[] ps = polyType.tparams;
		    Type res = polyType.result;
		    if (ps.length != ps1.length) return false;
		    for (int i = 0; i < ps.length; i++)
			if (!ps1[i].info().subst(ps1, ps).isSameAs(ps[i].info()) ||
			    !ps[i].loBound().isSameAs(ps1[i].loBound().subst(ps1, ps)) ||
			    !ps[i].vuBound().isSameAs(ps1[i].vuBound().subst(ps1, ps)))
			    return false;
		    return res.overrides(res1.subst(ps1, ps));
		}
		return false;
	    } else if (tp instanceof Type.OverloadedType) {
		throw new ApplicationError("overrides inapplicable for " + tp);
	    } else {
		if (this instanceof Type.MethodType || this instanceof Type.PolyType)
		    return false;
		return true;
	    }
	}
    }

// Closures and Least Upper Bounds ---------------------------------------------------

    /** The closure of this type, i.e. the widened type itself followed by all
     *  its direct and indirect (pre-) base types, sorted by Symbol.isLess().
     */
    public Type[] closure() {
        Type widened = this.widen().unalias();
        if (widened instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)widened;
            return subst(
                asSeenFrom(typeRef.sym.closure(), typeRef.pre, typeRef.sym.owner()),
                typeRef.sym.typeParams(), typeRef.args);
        } else if (widened instanceof CompoundType) {
/*
	    if (symbol().isCompoundSym()) {
		Type[][] closures = new Type[parts.length][];
		for (int i = 0; i < parts.length; i++)
		    closures[i] = parts[i].closure();
		return union(closures);
	    } else {
*/
	    return symbol().closure();
        }
        return new Type[]{this};
    }

    /** return union of array of closures. It is assumed that
     *  for any two base types with the same class symbols the later one
     *  is a subtype of the former.
     */
    static private Type[] union(Type[][] closures) {
        if (closures.length == 1) return closures[0]; // fast special case
        int[] index = new int[closures.length];
        int totalsize = 0;
        for (int i = 0; i < index.length; i++) {
            index[i] = 0;
            totalsize = totalsize + closures[i].length;
        }
        Type[] res = new Type[totalsize];
        int j = 0;

        while (true) {
            // find minimal element
            Type min = null;
            for (int i = 0; i < index.length; i++) {
                if (index[i] < closures[i].length) {
                    Type cltype = closures[i][index[i]];
                    if (min == null ||
                        cltype.symbol().isLess(min.symbol()) ||
                        cltype.symbol() == min.symbol()) {
                        min = cltype;
                    }
                }
            }
            if (min == null) break;

            res[j] = min;
            j = j + 1;

            // bump all indices that start with minimal element
            for (int i = 0; i < index.length; i++) {
                if (index[i] < closures[i].length &&
                    closures[i][index[i]].symbol() == min.symbol())
                    index[i] = index[i] + 1;
            }
        }
        Type[] result = new Type[j];
        System.arraycopy(res, 0, result, 0, j);
        return result;
    }

    /** return intersection of non-empty array of closures
     */
    static private Type[] intersection(Type[][] closures) {
        if (closures.length == 1) return closures[0]; // fast special case
        int[] index = new int[closures.length];
        Type[] mintypes = new Type[closures.length];
        int minsize = Integer.MAX_VALUE;
        for (int i = 0; i < index.length; i++) {
            index[i] = 0;
            if (closures[i].length < minsize) minsize = closures[i].length;
        }
        Type[] res = new Type[minsize];
        int j = 0;

        L:
        while (true) {
            // find minimal element
            Symbol minsym = null;
            for (int i = 0; i < index.length; i++) {
                if (index[i] == closures[i].length) break L;
                Symbol clsym = closures[i][index[i]].symbol();
                if (minsym == null || clsym.isLess(minsym)) minsym = clsym;
            }

            boolean agree = true;
            // bump all indices that start with minimal element
            for (int i = 0; i < index.length; i++) {
                Type cltype = closures[i][index[i]];
                if (cltype.symbol() == minsym) {
                    mintypes[i] = cltype;
                    index[i] = index[i] + 1;
                } else {
                    agree = false;
                }
            }
            if (agree) {
                Type mintype = argLub(mintypes);
                if (mintype.symbol().kind == CLASS) {
                    res[j] = mintype;
                    j = j + 1;
                }
            }
        }
        Type[] result = new Type[j];
        System.arraycopy(res, 0, result, 0, j);
        return result;
    }

    /** same as lub, but all types are instances of the same class,
     *  possibly with different prefixes and arguments.
     */
    //todo: catch lubs not within bounds.
    static Type argLub(Type[] tps) {
        tps = elimRedundant(tps, true);
        if (tps.length == 1) return tps[0];

        Type pre = tps[0].prefix();
        Symbol sym = tps[0].symbol();
        Symbol[] tparams = sym.typeParams();
        Type[] args = new Type[tparams.length];
        Type[][] argss = new Type[args.length][tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)tps[i];
                Type pre1 = typeRef.pre;
                Symbol sym1 = typeRef.sym;
                Type[] args1 = typeRef.args;
                assert sym == sym1;
                assert args1.length == args.length;
                if (!pre.isSameAs(pre1)) return NoType;
                for (int j = 0; j < args1.length; j++)
                    argss[j][i] = args1[j];
            } else if (tps[i] == ErrorType) {
                return ErrorType;
            } else {
                assert false : tps[i];
            }
        }
        for (int j = 0; j < args.length; j++) {
            if ((tparams[j].flags & COVARIANT) != 0)
                args[j] = lub(argss[j]);
            else if ((tparams[j].flags & CONTRAVARIANT) != 0)
                args[j] = glb(argss[j]);
            else return NoType;
        }
        return typeRef(pre, sym, args);
    }

    /** The frontier of a closure C is the minimal set of types such that
     *  the union of the closures of these types equals C.
     */
    static private Type[] frontier(Type[] closure) {
        Type[] front = new Type[closure.length];
        int j = 0;
        for (int i = 0; i < closure.length; i++) {
            int k = 0;
            Type tp = closure[i];
            while (k < j && !front[k].symbol().isSubClass(tp.symbol()))
                 k++;
            if (k == j) {
                front[j] = tp;
                j++;
            }
        }
        Type[] result = new Type[j];
        System.arraycopy(front, 0, result, 0, j);
        return result;
    }

    /** remove types that are subtypes of some other type.
     */
    static private Type[] elimRedundant(Type[] tps, boolean elimLower) {
        Type.List tl = Type.List.EMPTY;
        int nredundant = 0;
        boolean[] redundant = new boolean[tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] == ErrorType) {
                return new Type[]{ErrorType};
            } else if (tps[i] instanceof MethodType
                       || tps[i] instanceof PolyType
                       || tps[i] instanceof OverloadedType) {
                return new Type[]{NoType};
            } else {
                assert tps[i].isObjectType(): tps[i];
                for (int j = 0; j < i && !redundant[i]; j++) {
                    if (!redundant[j]) {
                        if (tps[i].isSubType(tps[j])) {
                            redundant[elimLower ? i : j] = true;
                            nredundant++;
                        } else if (tps[j].isSubType(tps[i])) {
                            redundant[elimLower ? j : i] = true;
                            nredundant++;
                        }
                    }
                }
            }
        }

        if (nredundant != 0) {
            Type[] tps1 = new Type[tps.length - nredundant];
            int n = 0;
            for (int i = 0; i < tps.length; i++) {
                if (!redundant[i]) tps1[n++] = tps[i];
            }
            return tps1;
        } else {
            return tps;
        }
    }

    static int recCount = 0;
    static boolean giveUp = false;
    static int recLimit = 10;

    public static Type lub(Type[] tps) {
	if (recCount == recLimit) {
	    giveUp = true;
	    return Global.instance.definitions.ANY_TYPE();
	} else {
	    recCount++;
	    Type result = lub0(tps);
	    recCount--;
	    if (recCount == 0) {
		if (giveUp) {
		    giveUp = false;
		    throw new Error("failure to compute least upper bound of types " +
				    ArrayApply.toString(tps, "", " and ", ";\n") +
				    "an approximation is: " + result + ";\n" +
				    "additional type annotations are needed");
		} else {
		    giveUp = false;
		}
	    }
	    return result;
	}
    }

    /** Return the least upper bound of non-empty array of types `tps'.
     */
    public static Type lub0(Type[] tps) {
        //System.out.println("lub" + ArrayApply.toString(tps));//DEBUG

        if (tps.length == 0) return Global.instance.definitions.ALL_TYPE();

        //If all types are method types with same parameters,
        //compute lub of their result types.
        if (tps[0] instanceof PolyType) {
            return polyLub(tps, ((PolyType)tps[0]).tparams);
        } else if (tps[0] instanceof MethodType) {
            return methodLub(tps, ((MethodType)tps[0]).vparams);
        }

        // remove types that are subtypes of some other type.
        tps = elimRedundant(tps, true);
        if (tps.length == 1) return tps[0];

        // intersect closures and build frontier.
        Type[][] closures = new Type[tps.length][];
        for (int i = 0; i < tps.length; i++) {
            closures[i] = tps[i].closure();
        }
        Type[] allBaseTypes = intersection(closures);
        Type[] leastBaseTypes = frontier(allBaseTypes);
        assert leastBaseTypes.length > 0 : ArrayApply.toString(tps);

        // add refinements where necessary
        Scope members = new Scope();
        Type lubType = compoundTypeWithOwner(Symbol.NONE, leastBaseTypes, members); // !!! NONE
	/*
        Type lubThisType = lubType.narrow();
        //System.out.println("lubtype = " + lubType);//DEBUG

        Symbol[] rsyms = new Symbol[tps.length];
        Type[] rtps = new Type[tps.length];
        Type[] rlbs = new Type[tps.length];
        for (int i = 0; i < allBaseTypes.length; i++) {
            for (Scope.SymbolIterator it = allBaseTypes[i].members().iterator();
                 it.hasNext(); ) {
                Symbol sym = it.next();
                Name name = sym.name;
                if ((sym.flags & PRIVATE) == 0 && lubType.lookup(name) == sym) {
                    Type symType = memberTp(lubThisType, sym);
                    Type symLoBound = lubThisType.memberLoBound(sym);
                    int j = 0;
                    while (j < tps.length) {
                        rsyms[j] = tps[j].lookupNonPrivate(name);
                        if (rsyms[j] == sym) break;
                        rtps[j] = memberTp(tps[j], rsyms[j])
                            .substThis(tps[j].symbol(), lubThisType);
                        rlbs[j] = tps[j].memberLoBound(rsyms[j])
                            .substThis(tps[j].symbol(), lubThisType);
                        if (rtps[j].isSameAs(symType) &&
                            rlbs[j].isSameAs(symLoBound)) break;
                        j++;
                    }
                    if (j == tps.length) {
			if (Global.instance.debug)
			    System.out.println("refinement lub for " +
			    ArrayApply.toString(rsyms) + ":" + ArrayApply.toString(rtps));//debug
                        Symbol lubSym = lub(rsyms, rtps, rlbs, lubType.symbol());
                        if (lubSym.kind != NONE &&
                            !(lubSym.kind == sym.kind &&
                              lubSym.info().isSameAs(symType) &&
                              lubSym.loBound().isSameAs(symType)))
                            members.enter(lubSym);
                    }
                }
            }
        }
        //System.out.print("lub "); System.out.print(ArrayApply.toString(tps)); System.out.println(" = " + lubType);//DEBUG
	*/
        if (leastBaseTypes.length == 1 && members.isEmpty())
            return leastBaseTypes[0];
        else return lubType;
    }
    //where
        private static Type memberTp(Type base, Symbol sym) {
            return sym.kind == CLASS ? base.memberType(sym) : base.memberInfo(sym);
        }

    private static Type polyLub(Type[] tps, Symbol[] tparams0) {
        Type[][] hiboundss = new Type[tparams0.length][tps.length];
        Type[][] loboundss = new Type[tparams0.length][tps.length];
        Type[][] vuboundss = new Type[tparams0.length][tps.length];
        Type[] restps   = new Type[tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof PolyType) {
                PolyType polyType = (PolyType)tps[i];
                Symbol[] tparams = polyType.tparams;
                Type restp = polyType.result;
                if (tparams.length == tparams0.length) {
                    for (int j = 0; j < tparams0.length; j++) {
                        hiboundss[j][i] = tparams[j].info()
                            .subst(tparams, tparams0);
                        loboundss[j][i] = tparams[j].loBound()
                            .subst(tparams, tparams0);
                        vuboundss[j][i] = tparams[j].vuBound()
                            .subst(tparams, tparams0);
                    }
                    restps[i] = restp.subst(tparams, tparams0);
                } else {
                    return Type.NoType;
                }
            } else {
                return Type.NoType;
            }
        }
        Type[] hibounds = new Type[tparams0.length];
        Type[] lobounds = new Type[tparams0.length];
        Type[] vubounds = new Type[tparams0.length];
        for (int j = 0; j < tparams0.length; j++) {
            hibounds[j] = glb(hiboundss[j]);
            lobounds[j] = lub(loboundss[j]);
            vubounds[j] = glb(vuboundss[j]);
        }
        Symbol[] tparams = new Symbol[tparams0.length];
        for (int j = 0; j < tparams.length; j++) {
            tparams[j] = tparams0[j].cloneSymbol(Symbol.NONE)
                .setInfo(hibounds[j].subst(tparams0, tparams))
                .setLoBound(lobounds[j].subst(tparams0, tparams))
                .setVuBound(vubounds[j].subst(tparams0, tparams));
        }
        return Type.PolyType(tparams, lub(restps).subst(tparams0, tparams));
    }

    private static Type methodLub(Type[] tps, Symbol[] vparams0) {
        Type[] restps = new Type[tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof MethodType) {
                MethodType methodType = (MethodType)tps[i];
                Symbol[] vparams = methodType.vparams;
                Type restp = methodType.result;
                if (vparams.length != vparams0.length)
                    return Type.NoType;
                for (int j = 0; j < vparams.length; j++)
                    if (!vparams[j].type().isSameAs(vparams0[j].type()) ||
                        (vparams[j].flags & (DEF | REPEATED)) !=
                        (vparams0[j].flags & (DEF | REPEATED)))
                        return Type.NoType;
                restps[i] = restp;
            } else {
                return Type.NoType;
            }
        }
        Symbol[] vparams = new Symbol[vparams0.length];
        for (int j = 0; j < vparams.length; j++) {
            vparams[j] = vparams0[j].cloneSymbol(Symbol.NONE);
        }
        return Type.MethodType(vparams, lub(restps));
    }

    private static Symbol lub(Symbol[] syms, Type[] tps, Type[] lbs, Symbol owner) {
        //System.out.println("lub" + ArrayApply.toString(syms));//DEBUG
        int lubKind = syms[0].kind;
        for (int i = 1; i < syms.length; i++) {
            Symbol sym = syms[i];
            if (sym.isError()) return Symbol.NONE;
            if (sym.isType() && sym.kind != lubKind) lubKind = TYPE;
        }
        if (lubKind == syms[0].kind && tps[0].isSameAsAll(tps)) {
            return syms[0].cloneSymbol();
        }

        Type lubType = lub(tps);
        if (lubType == Type.NoType) return Symbol.NONE;
        Symbol lubSym;
        switch (lubKind) {
        case VAL:
            lubSym = owner.newTerm(syms[0].pos, 0, syms[0].name);
            break;
        case TYPE: case ALIAS: case CLASS:
            lubSym = owner.newAbstractType(syms[0].pos, 0, syms[0].name);
            lubSym.setLoBound(glb(lbs));
            break;
        default:
            throw new ApplicationError();
        }
        lubSym.setInfo(lubType.setOwner(lubSym));
        return lubSym;
    }

    public static Type glb(Type[] tps) {
	if (recCount == recLimit) {
	    giveUp = true;
	    return Global.instance.definitions.ALL_TYPE();
	} else {
	    recCount++;
	    Type result = glb0(tps);
	    recCount--;
	    if (recCount == 0) {
		if (giveUp) {
		    giveUp = false;
		    throw new Error("failure to compute greatest lower bound of types " +
				    ArrayApply.toString(tps, "", " and ", ";\n") +
				    "an approximation is: " + result + ";\n" +
				    "additional type annotations are needed");
		} else {
		    giveUp = false;
		}
	    }
	    return result;
	}
    }

    public static Type glb0(Type[] tps) {
        if (tps.length == 0) return Global.instance.definitions.ANY_TYPE();

        // step one: eliminate redunandant types; return if one one is left
        tps = elimRedundant(tps, false);
        if (tps.length == 1) return tps[0];

        // step two: build arrays of all typerefs and all refinements
        Type.List treftl = Type.List.EMPTY;
        Type.List comptl = Type.List.EMPTY;
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof TypeRef) {
                treftl = new Type.List(tps[i], treftl);
            } else if (tps[i] instanceof CompoundType) {
                CompoundType compoundType = (CompoundType)tps[i];
                if (!compoundType.members.isEmpty())
                    comptl = new Type.List(tps[i], comptl);
                for (int j = 0; j < compoundType.parts.length; j++)
                    treftl = new Type.List(compoundType.parts[j], treftl);
            } else if (tps[i] instanceof ThisType
                       || tps[i] instanceof SingleType
                       || tps[i] instanceof ConstantType) {
                return Global.instance.definitions.ALL_TYPE();
            }
        }

        CompoundType glbType = compoundTypeWithOwner(Symbol.NONE, Type.EMPTY_ARRAY, new Scope()); // !!! NONE
        Type glbThisType = glbType.narrow();

        // step 3: compute glb of all refinements.
        Scope members = Scope.EMPTY;
        if (comptl != List.EMPTY) {
            Type[] comptypes = comptl.toArrayReverse();
            Scope[] refinements = new Scope[comptypes.length];
            for (int i = 0; i < comptypes.length; i++)
                refinements[i] = comptypes[i].members();
            if (!setGlb(glbType.members, refinements, glbThisType)) {
                // refinements don't have lower bound, so approximate
                // by AllRef
                glbType.members = Scope.EMPTY;
                treftl = new Type.List(
                    Global.instance.definitions.ALLREF_TYPE(), treftl);
            }
        }

        // eliminate redudant typerefs
        Type[] treftypes = elimRedundant(treftl.toArrayReverse(), false);
        if (treftypes.length != 1 || !glbType.members.isEmpty()) {
            // step 4: replace all abstract types by their lower bounds.
            boolean hasAbstract = false;
            for (int i = 0; i < treftypes.length; i++) {
                if (treftypes[i].unalias().symbol().kind == TYPE)
                    hasAbstract = true;
            }
            if (hasAbstract) {
                treftl = Type.List.EMPTY;
                for (int i = 0; i < treftypes.length; i++) {
                    if (treftypes[i].unalias().symbol().kind == TYPE)
                        treftl = new Type.List(treftypes[i].loBound(), treftl);
                    else
                        treftl = new Type.List(treftypes[i], treftl);
                }
                treftypes = elimRedundant(treftl.toArrayReverse(), false);
            }
        }

        if (treftypes.length != 1) {
            // step 5: if there are conflicting instantiations of same
            // class, replace them by lub/glb of arguments or lower bound.
            Type lb = NoType;
            for (int i = 0;
                 i < treftypes.length &&
                     lb.symbol() != Global.instance.definitions.ALL_CLASS;
                 i++) {
                for (int j = 0; j < i; j++) {
                    if (treftypes[j].symbol() == treftypes[i].symbol())
                        lb = argGlb(treftypes[j], treftypes[i]);
                }
            }
            if (lb != NoType) return lb;
        }

        if (treftypes.length == 1 && glbType.members.isEmpty()) {
            return treftypes[0];
        } else {
            glbType.parts = treftypes;
            return glbType;
        }
    }

    private static Type argGlb(Type tp1, Type tp2) {
        if (tp1 instanceof TypeRef && tp2 instanceof TypeRef) {
            TypeRef typeRef1 = (TypeRef)tp1;
            TypeRef typeRef2 = (TypeRef)tp2;
            assert typeRef1.sym == typeRef2.sym;
            if (typeRef1.pre.isSameAs(typeRef2.pre)) {
                Symbol[] tparams = typeRef1.sym.typeParams();
                Type[] args = new Type[tparams.length];
                for (int i = 0; i < tparams.length; i++) {
                    if (typeRef1.args[i].isSameAs(typeRef2.args[i]))
                        args[i] = typeRef1.args[i];
                    else if ((tparams[i].flags & COVARIANT) != 0)
                        args[i] = lub(new Type[]{typeRef1.args[i], typeRef2.args[i]});
                    else if ((tparams[i].flags & CONTRAVARIANT) != 0)
                        args[i] = glb(new Type[]{typeRef1.args[i], typeRef2.args[i]});
                    else
                        return glb(new Type[]{tp1.loBound(), tp2.loBound()});
                }
                return typeRef(typeRef1.pre, typeRef1.sym, args);
            }
        }
        return glb(new Type[]{tp1.loBound(), tp2.loBound()});
    }

    /** Set scope `result' to glb of scopes `ss'. Return true iff succeeded.
     */
    private static boolean setGlb(Scope result, Scope[] ss, Type glbThisType) {
        for (int i = 0; i < ss.length; i++)
            for (Scope.SymbolIterator it = ss[i].iterator(); it.hasNext(); )
                if (!addMember(result, it.next(), glbThisType)) return false;
        return true;
    }

    /** Add member `sym' to scope `s'. If`s' has already a member with same name,
     *  overwrite its info/low bound to form glb of both symbols.
     */
    private static boolean addMember(Scope s, Symbol sym, Type glbThisType) {
        Type syminfo = sym.info().substThis(sym.owner(), glbThisType);
        Type symlb = sym.loBound().substThis(sym.owner(), glbThisType);
        Type symvb = sym.vuBound().substThis(sym.owner(), glbThisType);
        Scope.Entry e = s.lookupEntry(sym.name);
        if (e == Scope.Entry.NONE) {
            Symbol sym1 = sym.cloneSymbol(glbThisType.symbol());
            sym1.setInfo(syminfo);
            if (sym1.kind == TYPE) {
		sym1.setLoBound(symlb);
		sym1.setVuBound(symvb);
	    }
            s.enter(sym1);
        } else {
            Type einfo = e.sym.info();
            if (einfo.isSameAs(syminfo)) {
            } else if (einfo.isSubType(syminfo) && sym.kind != ALIAS) {
            } else if (syminfo.isSubType(einfo) && e.sym.kind != ALIAS) {
                e.sym.setInfo(syminfo);
            } else if (sym.kind == VAL && e.sym.kind == VAL ||
                       sym.kind == TYPE && e.sym.kind == TYPE) {
                e.sym.setInfo(glb(new Type[]{einfo, syminfo}).setOwner(e.sym));
            } else {
                return false;
            }
            if (e.sym.kind == TYPE && sym.kind == TYPE) {
                Type elb = e.sym.loBound();
                if (elb.isSameAs(symlb)) {
                } else if (symlb.isSubType(elb)) {
                } else if (elb.isSubType(symlb)) {
                    e.sym.setLoBound(symlb);
                } else {
                    e.sym.setLoBound(lub(new Type[]{elb, symlb}));
                }
                Type evb = e.sym.vuBound();
                if (evb.isSameAs(symvb)) {
                } else if (evb.isSubType(symvb)) {
                } else if (symvb.isSubType(evb)) {
                    e.sym.setVuBound(symvb);
                } else {
                    e.sym.setVuBound(glb(new Type[]{evb, symvb}));
                }
            }
        }
        return true;
    }

    private static Type polyGlb(Type[] tps, Symbol[] tparams0) {
        Type[][] hiboundss = new Type[tparams0.length][tps.length];
        Type[][] loboundss = new Type[tparams0.length][tps.length];
        Type[][] vuboundss = new Type[tparams0.length][tps.length];
        Type[] restps   = new Type[tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof PolyType) {
                PolyType polyType = (PolyType)tps[i];
                Symbol[] tparams = polyType.tparams;
                Type restp = polyType.result;
                if (tparams.length == tparams0.length) {
                    for (int j = 0; j < tparams0.length; j++) {
                        hiboundss[j][i] = tparams[j].info()
                            .subst(tparams, tparams0);
                        loboundss[j][i] = tparams[j].loBound()
                            .subst(tparams, tparams0);
                        vuboundss[j][i] = tparams[j].vuBound()
                            .subst(tparams, tparams0);
                    }
                    restps[i] = restp.subst(tparams, tparams0);
                } else {
                    return Type.NoType;
                }
            } else {
                return Type.NoType;
            }
        }
        Type[] hibounds = new Type[tparams0.length];
        Type[] lobounds = new Type[tparams0.length];
        Type[] vubounds = new Type[tparams0.length];
        for (int j = 0; j < tparams0.length; j++) {
            hibounds[j] = lub(hiboundss[j]);
            lobounds[j] = glb(loboundss[j]);
            vubounds[j] = lub(vuboundss[j]);
        }
        Symbol[] tparams = new Symbol[tparams0.length];
        for (int j = 0; j < tparams.length; j++) {
            tparams[j] = tparams0[j].cloneSymbol(Symbol.NONE)
                .setInfo(hibounds[j].subst(tparams0, tparams))
                .setLoBound(lobounds[j].subst(tparams0, tparams))
                .setVuBound(vubounds[j].subst(tparams0, tparams));
        }
        return Type.PolyType(tparams, glb(restps).subst(tparams0, tparams));
    }

    private static Type methodGlb(Type[] tps, Symbol[] vparams0) {
        Type[] restps = new Type[tps.length];
        for (int i = 0; i < tps.length; i++) {
            if (tps[i] instanceof MethodType) {
                MethodType methodType = (MethodType)tps[i];
                Symbol[] vparams = methodType.vparams;
                Type restp = methodType.result;
                if (vparams.length != vparams0.length)
                    return Type.NoType;
                for (int j = 0; j < vparams.length; j++)
                    if (!vparams[i].type().isSameAs(vparams0[i].type()) ||
                        (vparams[i].flags & (DEF | REPEATED)) !=
                        (vparams0[i].flags & (DEF | REPEATED)))
                        return Type.NoType;
                restps[i] = restp;
            } else {
                return Type.NoType;
            }
        }
        Symbol[] vparams = new Symbol[vparams0.length];
        for (int j = 0; j < vparams.length; j++) {
            vparams[j] = vparams0[j].cloneSymbol(Symbol.NONE);
        }
        return Type.MethodType(vparams, glb(restps));
    }

// Erasure --------------------------------------------------------------------------

    public static Map erasureMap = new MapOnlyTypes() {
        public Type apply(Type t) { return t.erasure(); }
    };

    private static final Type[] unboxedType =
        new Type[LastUnboxedTag + 1 - FirstUnboxedTag];
    private static final Name[] unboxedName =
        new Name[LastUnboxedTag + 1 - FirstUnboxedTag];
    private static final Symbol[] boxedSymbol =
        new Symbol[LastUnboxedTag + 1 - FirstUnboxedTag];

    private static void mkStdClassType(int kind, String unboxedstr, Symbol boxedsym) {
        unboxedType[kind - FirstUnboxedTag] = UnboxedType(kind);
        unboxedName[kind - FirstUnboxedTag] = Name.fromString(unboxedstr);
        boxedSymbol[kind - FirstUnboxedTag] = boxedsym;
    }

    static void initializeUnboxedTypes(Definitions definitions) {
        mkStdClassType(BYTE, "byte", definitions.BYTE_CLASS);
        mkStdClassType(SHORT, "short", definitions.SHORT_CLASS);
        mkStdClassType(CHAR, "char", definitions.CHAR_CLASS);
        mkStdClassType(INT, "int", definitions.INT_CLASS);
        mkStdClassType(LONG, "long", definitions.LONG_CLASS);
        mkStdClassType(FLOAT, "float", definitions.FLOAT_CLASS);
        mkStdClassType(DOUBLE, "double", definitions.DOUBLE_CLASS);
        mkStdClassType(BOOLEAN, "boolean", definitions.BOOLEAN_CLASS);
        mkStdClassType(UNIT, "void", definitions.UNIT_CLASS);
    }

    /** Return unboxed type of given kind.
     */
    public static Type unboxedType(int kind) {
        return unboxedType[kind - FirstUnboxedTag];
    }

    /** Return the name of unboxed type of given kind.
    */
    public static Name unboxedName(int kind) {
        return unboxedName[kind - FirstUnboxedTag];
    }

    /** If type is boxed, return its unboxed equivalent; otherwise return the type
     *  itself.
     */
    public Type unbox() {
        if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            Symbol clasz = typeRef.sym;
            Type[] args = typeRef.args;
            if (args.length == 0) {
                for (int i = 0; i < boxedSymbol.length; i++)
                    if (boxedSymbol[i] == clasz) return unboxedType[i];
            } else if (args.length == 1) {
                Definitions definitions = Global.instance.definitions;
                if (clasz == definitions.ARRAY_CLASS) {
                    Type item = args[0];
                    Type bound = item.upperBound();
                    // todo: check with Philippe if this is what we want.
                    if (item.symbol().isClass() ||
                        (bound.symbol() != definitions.ANY_CLASS &&
                            bound.symbol() != definitions.ANYVAL_CLASS))
                    {
                        return UnboxedArrayType(args[0].erasure());
                    }
                }
            }
        }
        return this;
    }
    //where
        private Type upperBound() {
            if (this instanceof TypeRef) {
                TypeRef typeRef = (TypeRef)this;
                if (typeRef.sym.kind == TYPE)
                    return typeRef.pre.memberInfo(typeRef.sym).upperBound();
            }
            return this;
        }

    /** Return the erasure of this type.
     */
    public Type erasure() {
        if (this instanceof ThisType
            || this instanceof SingleType
            || this instanceof ConstantType) {
            return singleDeref().erasure();
        } else if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            Type pre = typeRef.pre;
            Symbol sym = typeRef.sym;
            switch (sym.kind) {
            case ALIAS: case TYPE:
                return sym.info().asSeenFrom(pre, sym.owner()).erasure();

            case CLASS:
                Definitions definitions = Global.instance.definitions;
                if (sym == definitions.UNIT_CLASS) return this;
                if (sym == definitions.OBJECT_CLASS ||
                    sym == definitions.ALL_CLASS ||
                    sym == definitions.ALLREF_CLASS)
                    return Type.typeRef(localThisType, definitions.ANY_CLASS, EMPTY_ARRAY);
                else {
                    Type this1 = unbox();
                    if (this1 != this) return this1;
                    else return Type.typeRef(localThisType, sym, EMPTY_ARRAY);
                }

            default: throw new ApplicationError(sym + " has wrong kind: " + sym.kind);
            }
        } else if (this instanceof CompoundType) {
            Type[] parents = ((CompoundType)this).parts;
            if (parents.length > 0) return parents[0].erasure();
            else return this;
        } else if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
            Symbol[] params1 = erasureMap.map(methodType.vparams);
            Type tp1 = methodType.result.fullErasure();
            if (tp1 instanceof MethodType) {
                MethodType nestedMethodType = (MethodType)tp1;
                Symbol[] newparams = new Symbol[params1.length + nestedMethodType.vparams.length];
                System.arraycopy(params1, 0, newparams, 0, params1.length);
                System.arraycopy(nestedMethodType.vparams, 0, newparams, params1.length, nestedMethodType.vparams.length);
                return MethodType(newparams, nestedMethodType.result);
            } else {
                if (params1 == methodType.vparams && tp1 == methodType.result) return this;
                else return MethodType(params1, tp1);
            }
        } else if (this instanceof PolyType) {
            return ((PolyType)this).result.erasure();
        }
        return erasureMap.map(this);
    }

    /** Return the full erasure of the type. Full erasure is the same
     * as "normal" erasure, except that the "Unit" type is erased to
     * the "void" type.
     */
    public Type fullErasure() {
        Type erasure = erasure();
        if (Global.instance.definitions.UNIT_CLASS == erasure.symbol())
            erasure = erasure.unbox();
        return erasure;
    }

// Object Interface -----------------------------------------------------------------

    public String toString() {
        return new SymbolTablePrinter().printType(this).toString();
    }

    public String toLongString() {
        String str = toString();
        if (str.endsWith(".type")) return str + " (with underlying type " + widen() + ")";
        else return str;
    }

    public int hashCode() {
        if (this == ErrorType) {
            return ERRORtpe;
        } else if (this == NoType) {
            return NOtpe;
        } else if (this == NoPrefix) {
            return NOpre;
        } else if (this instanceof ThisType) {
            Symbol sym = ((ThisType)this).sym;
            return THIStpe
                ^ (sym.hashCode() * 41);
        } else if (this instanceof TypeRef) {
            TypeRef typeRef = (TypeRef)this;
            return TYPEREFtpe
                ^ (typeRef.pre.hashCode() * 41)
                ^ (typeRef.sym.hashCode() * (41*41))
                ^ (hashCode(typeRef.args) * (41*41*41));
        } else if (this instanceof SingleType) {
            SingleType singleType = (SingleType)this;
            return SINGLEtpe
                ^ (singleType.pre.hashCode() * 41)
                ^ (singleType.sym.hashCode() * (41*41));
        } else if (this instanceof ConstantType) {
            ConstantType constantType = (ConstantType)this;
            return CONSTANTtpe
                ^ (constantType.base.hashCode() * 41)
                ^ (constantType.value.hashCode() * (41*41));
        } else if (this instanceof CompoundType) {
            return symbol().hashCode();
            //return COMPOUNDtpe
            //  ^ (hashCode(parts) * 41)
            //  ^ (members.hashCode() * (41 * 41));
        } else if (this instanceof MethodType) {
            MethodType methodType = (MethodType)this;
            int h = METHODtpe;
            for (int i = 0; i < methodType.vparams.length; i++)
                h = (h << 4) ^ (methodType.vparams[i].flags & SOURCEFLAGS);
            return h
                ^ (hashCode(Symbol.type(methodType.vparams)) * 41)
                ^ (methodType.result.hashCode() * (41 * 41));
        } else if (this instanceof PolyType) {
            PolyType polyType = (PolyType)this;
            return POLYtpe
                ^ (hashCode(polyType.tparams) * 41)
                ^ (polyType.result.hashCode() * (41 * 41));
        } else if (this instanceof OverloadedType) {
            OverloadedType overloadedType = (OverloadedType)this;
            return OVERLOADEDtpe
                ^ (hashCode(overloadedType.alts) * 41)
                ^ (hashCode(overloadedType.alttypes) * (41 * 41));
        } else if (this instanceof UnboxedType) {
            return UNBOXEDtpe
                ^ (((UnboxedType)this).tag * 41);
        } else if (this instanceof UnboxedArrayType) {
            return UNBOXEDARRAYtpe
                ^ (((UnboxedArrayType)this).elemtp.hashCode() * 41);
        }
        throw new ApplicationError();
    }

    public static int hashCode(Object[] elems) {
        int h = 0;
        for (int i = 0; i < elems.length; i++)
            h = h * 41 + elems[i].hashCode();
        return h;
    }

    // todo: change in relation to needs.

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof Type) {
            Type that = (Type) other;
            if (this == ErrorType) {
                return that == ErrorType;
            } else if (this == NoType) {
                return that == NoType;
            } else if (this == NoPrefix) {
                return that == NoPrefix;
            } else if (this instanceof ThisType) {
                if (that instanceof ThisType) {
                    return ((ThisType)this).sym == ((ThisType)that).sym;
                }
                return false;
            } else if (this instanceof TypeRef) {
                if (that instanceof TypeRef) {
                    TypeRef thisTypeRef = (TypeRef)this;
                    TypeRef thatTypeRef = (TypeRef)that;
                    return thisTypeRef.pre.equals(thatTypeRef.pre)
                        && thisTypeRef.sym == thatTypeRef.sym
                        && equals(thisTypeRef.args, thatTypeRef.args);
                }
                return false;
            } else if (this instanceof SingleType) {
                if (that instanceof SingleType) {
                    SingleType thisSingleType = (SingleType)this;
                    SingleType thatSingleType = (SingleType)that;
                    return thisSingleType.pre.equals(thatSingleType.pre)
                        && thisSingleType.sym == thatSingleType.sym;
                }
                return false;
            } else if (this instanceof ConstantType) {
                if (that instanceof ConstantType) {
                    ConstantType thisConstantType = (ConstantType)this;
                    ConstantType thatConstantType = (ConstantType)that;
                    return thisConstantType.base.equals(thatConstantType.base)
                        && thisConstantType.value.equals(thatConstantType.value);
                }
                return false;
            } else if (this instanceof CompoundType) {
                if (that instanceof CompoundType) {
                    return this.symbol() == that.symbol();
                    //return parts.equals(parts1) && members.equals(members1);
                }
                return false;
            } else if (this instanceof MethodType) {
                if (that instanceof MethodType) {
                    MethodType thisMethodType = (MethodType)this;
                    MethodType thatMethodType = (MethodType)that;
                    if (thisMethodType.vparams.length != thatMethodType.vparams.length)
                        return false;
                    for (int i = 0; i < thisMethodType.vparams.length; i++)
                        if ((thisMethodType.vparams[i].flags & SOURCEFLAGS) !=
                            (thatMethodType.vparams[i].flags & SOURCEFLAGS))
                            return false;
                    return
                        equals(Symbol.type(thisMethodType.vparams), Symbol.type(thatMethodType.vparams)) &&
                        thisMethodType.result.equals(thatMethodType.result);
                }
                return false;
            } else if (this instanceof PolyType) {
                if (that instanceof PolyType) {
                    PolyType thisPolyType = (PolyType)this;
                    PolyType thatPolyType = (PolyType)that;
                    return equals(thisPolyType.tparams, thatPolyType.tparams)
                        && thisPolyType.result.equals(thatPolyType.result);
                }
                return false;
            } else if (this instanceof OverloadedType) {
                if (that instanceof OverloadedType) {
                    OverloadedType thisOverloadedType = (OverloadedType)this;
                    OverloadedType thatOverloadedType = (OverloadedType)that;
                    return equals(thisOverloadedType.alts, thatOverloadedType.alts)
                        && equals(thisOverloadedType.alttypes, thatOverloadedType.alttypes);
                }
                return false;
            } else if (this instanceof UnboxedType) {
                if (that instanceof UnboxedType) {
                    return ((UnboxedType)this).tag == ((UnboxedType)that).tag;
                }
                return false;
            } else if (this instanceof UnboxedArrayType) {
                if (that instanceof UnboxedArrayType) {
                    return ((UnboxedArrayType)this).elemtp.equals(((UnboxedArrayType)that).elemtp);
                }
                return false;
            }
        }
        return false;
    }

    public static boolean equals(Object[] elems1, Object[] elems2) {
        if (elems1.length != elems2.length) return false;
        for (int i = 0; i < elems1.length; i++) {
            if (!elems1[i].equals(elems2[i])) return false;
        }
        return true;
    }

// Type.List class -----------------------------------------------------------------

    /** A class for lists of types.
     */
    public static class List {
        public Type head;
        public List tail;
        public List(Type head, List tail) {
            this.head = head; this.tail = tail;
        }
        public int length() {
            return (this == EMPTY) ? 0 : 1 + tail.length();
        }
        public Type[] toArray() {
            Type[] ts = new Type[length()];
            copyToArray(ts, 0, 1);
            return ts;
        }
        public void copyToArray(Type[] ts, int start, int delta) {
            if (this != EMPTY) {
                ts[start] = head;
                tail.copyToArray(ts, start+delta, delta);
            }
        }
        public Type[] toArrayReverse() {
            Type[] ts = new Type[length()];
            copyToArray(ts, ts.length - 1, -1);
            return ts;
        }

        public String toString() {
            if (this == EMPTY) return "List()";
            else return head + "::" + tail;
        }

        public static List EMPTY = new List(null, null);

        public static List append(List l, Type tp) {
            return (l == EMPTY) ? new List(tp, EMPTY)
                : new List(l.head, append(l.tail, tp));
        }
    }

// Type.Constraint class -------------------------------------------------------

    /** A class for keeping sub/supertype constraints and instantiations
     *  of type variables.
     */
    public static class Constraint {
        public List lobounds = List.EMPTY;
        public List hibounds = List.EMPTY;
        public Type inst = NoType;

        public boolean instantiate(Type tp) {
            for (List l = lobounds; l != List.EMPTY; l = l.tail) {
                if (!l.head.isSubType(tp)) return false;
            }
            for (List l = hibounds; l != List.EMPTY; l = l.tail) {
                if (!tp.isSubType(l.head)) return false;
            }
            inst = tp;
            return true;
        }
    }

// Type.Error class --------------------------------------------------------------

    /** A class for throwing type errors
     */
    public static class Error extends java.lang.Error {
        public String msg;
        public Error(String msg) {
            super(msg);
            this.msg = msg;
        }
    }

    public static class Malformed extends Error {
        public Malformed(Type pre, String tp) {
            super("malformed type: " + pre + "#" + tp);
        }
    }

    /** A class for throwing type errors
     */
    public static class VarianceError extends Error {
        public VarianceError(String msg) {
            super(msg);
        }
    }

    public static void explainTypes(Type found, Type required) {
        if (Global.instance.explaintypes) {
            boolean s = explainSwitch;
            explainSwitch = true;
            found.isSubType(required);
            explainSwitch = s;
        }
    }
}

/* A standard pattern match:

    case ErrorType:
    case AnyType:
    case NoType:
    case ThisType(Symbol sym):
    case TypeRef(Type pre, Symbol sym, Type[] args):
    case SingleType(Type pre, Symbol sym):
    case ConstantType(Type base, Object value):
    case CompoundType(Type[] parts, Scope members):
    case MethodType(Symbol[] vparams, Type result):
    case PolyType(Symbol[] tparams, Type result):
    case OverloadedType(Symbol[] alts, Type[] alttypes):
*/
