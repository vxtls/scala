/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.transformer.matching;

import scala.tools.util.Position;

import scalac.*;
import scalac.ast.*;
import scalac.atree.AConstant;
import scalac.util.*;
import scalac.symtab.*;
import scalac.transformer.matching.PatternNode.*;
import scalac.ast.Tree.*;

class CodeFactory extends PatternTool {

    public int pos = Position.FIRSTPOS ;
    private final Symbol owner;

    public CodeFactory(CompilationUnit unit, int pos, Symbol owner ) {
	super( unit );
	this.pos = pos;
        this.owner = owner;
    }

    // --------- these are new

    /** a faked switch statement
     */
    Tree Switch( Tree condition[],
		 Tree body[],
		 Tree defaultBody ) {
	assert condition != null:"cond is null";
	assert body != null:"body is null";
	assert defaultBody != null:"defaultBody is null";
	Tree result = defaultBody;

	for( int i = condition.length-1; i >= 0; i-- )
	    result = gen.If(condition[i], body[i], result);

	return result ;
    }

    /** returns  `List[ Tuple2[ scala.Int, <elemType> ] ]' */
      Type SeqTraceType( Type elemType ) {
          return defs.LIST_TYPE(pairType(defs.int_TYPE(), elemType));
      }

    /**  returns `Iterator[ elemType ]' */
    Type _seqIterType( Type elemType ) {
        return defs.ITERATOR_TYPE(elemType);
    }

    /**  returns `<seqObj.elements>' */
    Tree newIterator( Tree seqObj, Type elemType ) {
	return gen.mkApply__(gen.Select(seqObj, defs.ITERABLE_ELEMENTS()));
    }

    /** returns code `<seqObj>.elements'
     *  the parameter needs to have type attribute `Sequence[<elemType>]'
     */
    Tree newIterator( Tree seqObj ) {
	return newIterator( seqObj, getElemType_Sequence( seqObj.getType() ));
    }

    // EXPERIMENTAL
    Tree newRef( Tree init ) {
	//System.out.println( "hello:"+refSym().type() );
	return gen.New( gen.mkApplyTV( gen.mkPrimaryConstructorGlobalRef( pos, defs.REF_CLASS),
                                                                    new Type[] { init.getType() },
                                                                    new Tree[] { init } ));
    }

    /** returns A for T <: Sequence[ A ]
     */
    Type getElemType_Sequence( Type tpe ) {
	//System.err.println("getElemType_Sequence("+tpe.widen()+")");
	Type tpe1 = tpe.widen().baseType( defs.SEQ_CLASS );

	if( tpe1 == Type.NoType )
	    throw new ApplicationError("arg "+tpe+" not subtype of Seq[ A ]");

	return tpe1.typeArgs()[ 0 ];
    }

    /** returns A for T <: Iterator[ A ]
     */
    Type getElemType_Iterator( Type tpe ) {
	//System.err.println("getElemType_Iterator("+tpe+")");

	Type tpe1 = tpe.widen().baseType( defs.ITERATOR_CLASS );

	if (tpe1 instanceof Type.TypeRef) {
	    return ((Type.TypeRef)tpe1).args[ 0 ];
	}
	throw new ApplicationError("arg "+tpe+" not subtype of Iterator[ A ]");

    }

    /** `it.next()'
     */
    public Tree _next( Tree iter ) {
	return gen.mkApply__(gen.Select(iter, defs.ITERATOR_NEXT()));
    }

    /** `it.hasNext()'
     */
    public Tree _hasNext( Tree iter ) {
	return gen.mkApply__(gen.Select(iter, defs.ITERATOR_HASNEXT()));
    }

    /** `!it.hasCur()'
     */
    public Tree _not_hasNext( Tree iter ) {
	return gen.mkApply__(gen.Select(_hasNext(iter), defs.BOOLEAN_NOT()));
    }

      /** `trace.isEmpty'
       */
      public Tree isEmpty( Tree iter ) {
          return gen.mkApply__(gen.Select(iter, defs.LIST_ISEMPTY()));
      }

    Tree SeqTrace_headElem( Tree arg ) { // REMOVE SeqTrace
	Tree t = gen.mkApply__(gen.Select(arg, defs.LIST_HEAD()));
	return gen.mkApply__(gen.Select(t, defs.TUPLE_FIELD(2, 2)));
    }

    Tree SeqTrace_headState( Tree arg ) { // REMOVE SeqTrace
	Tree t = gen.mkApply__(gen.Select(arg, defs.LIST_HEAD()));
	return gen.mkApply__(gen.Select(t, defs.TUPLE_FIELD(2, 1)));

    }

    Tree SeqTrace_tail( Tree arg ) { // REMOVE SeqTrace
	return gen.mkApply__(gen.Select(arg, defs.LIST_TAIL()));
    }

    /** `<seqlist>.head()'
     */
    Tree SeqList_head( Tree arg ) {
	return gen.mkApply__(gen.Select(arg, defs.LIST_HEAD()));
    }

     // unused
       public Tree Negate(Tree tree) {
       if (tree instanceof Literal) {
       AConstant value = ((Literal)tree).value;
       if (value instanceof AConstant.BooleanValue)
       return gen.mkBooleanLit(tree.pos, !((AConstant.BooleanValue)value).value);
       }
       return gen.mkApply__(gen.Select(tree, defs.BOOLEAN_NOT()));
       }

    protected Tree And(Tree left, Tree right) {
        if (left instanceof Literal) {
	    AConstant value = ((Literal)left).value;
	    if (value instanceof AConstant.BooleanValue)
	        return ((AConstant.BooleanValue)value).value ? right : left;
        }
        if (right instanceof Literal) {
	    AConstant value = ((Literal)right).value;
	    if (value instanceof AConstant.BooleanValue && ((AConstant.BooleanValue)value).value) return left;
        }
        Symbol result = owner.newFieldOrVariable(
            pos,
            Modifiers.SYNTHETIC | Modifiers.MUTABLE,
            fresh.newName(Name.fromString("$pm$and"))).setType(defs.BOOLEAN_TYPE());
        Tree resultRef = gen.Ident(pos, result);
        Tree[] stats = {
            gen.ValDef(result, gen.mkBooleanLit(pos, false))
        };
        Tree thenp = gen.mkBlock(
            pos,
            gen.Assign(resultRef.duplicate(), right),
            resultRef.duplicate());
        return gen.mkBlock(
            pos,
            stats,
            gen.If(left, thenp, resultRef.duplicate(), defs.BOOLEAN_TYPE()));
    }

    protected Tree Or(Tree left, Tree right) {
        if (left instanceof Literal) {
	    AConstant value = ((Literal)left).value;
	    if (value instanceof AConstant.BooleanValue)
	        return ((AConstant.BooleanValue)value).value ? left : right;
        }
        if (right instanceof Literal) {
	    AConstant value = ((Literal)right).value;
	    if (value instanceof AConstant.BooleanValue && !((AConstant.BooleanValue)value).value) return left;
        }
        Symbol result = owner.newFieldOrVariable(
            pos,
            Modifiers.SYNTHETIC | Modifiers.MUTABLE,
            fresh.newName(Name.fromString("$pm$or"))).setType(defs.BOOLEAN_TYPE());
        Tree resultRef = gen.Ident(pos, result);
        Tree[] stats = {
            gen.ValDef(result, gen.mkBooleanLit(pos, false))
        };
        Tree thenp = gen.mkBlock(
            pos,
            gen.Assign(resultRef.duplicate(), gen.mkBooleanLit(pos, true)),
            resultRef.duplicate());
        Tree elsep = gen.mkBlock(
            pos,
            gen.Assign(resultRef.duplicate(), right),
            resultRef.duplicate());
        return gen.mkBlock(
            pos,
            stats,
            gen.If(left, thenp, elsep, defs.BOOLEAN_TYPE()));
    }

    // used by Equals
    private Symbol getCoerceToInt(Type left) {
        Symbol sym = left.lookupNonPrivate(Names.coerce);
        if (sym == Symbol.NONE) {
            sym = left.lookupNonPrivate(Names.coerceToInt);
        }
        if (sym == Symbol.NONE) {
            return null;
        }
        Symbol[] syms = sym.alternativeSymbols();
        for (int i = 0; i < syms.length; i++) {
            Type info = syms[i].info();
            if (info instanceof Type.MethodType) {
                Type.MethodType methodType = (Type.MethodType)info;
                if (methodType.vparams.length == 0 && methodType.result.isSameAs(defs.INT_TYPE()))
                    return syms[i];
            }
        }
        return null;
    }

    // used by Equals
    private Symbol getEqEq(Type left, Type right) {
        Symbol sym = left.lookupNonPrivate(Names.EQEQ);
        assert sym != Symbol.NONE
            : Debug.show(left) + "::" + Debug.show(left.members());
        Symbol fun = null;
        Type ftype = defs.ANY_TYPE();
        Symbol[] syms = sym.alternativeSymbols();
        for (int i = 0; i < syms.length; i++) {
            Symbol[] vparams = syms[i].valueParams();
            if (vparams.length == 1) {
                Type vptype = vparams[0].info();
                if (right.isSubType(vptype) && vptype.isSubType(ftype)) {
                    fun = syms[i];
                    ftype = vptype;
                }
            }
        }
        assert fun != null : Debug.show(sym.info());
        return fun;
    }

    protected Tree Equals(Tree left, Tree right) {
        Type ltype = left.type.widen(), rtype = right.type.widen();
        if (ltype.isSameAs(rtype)
            && (ltype.isSameAs(defs.CHAR_TYPE())
                || ltype.isSameAs(defs.BYTE_TYPE())
                || ltype.isSameAs(defs.SHORT_TYPE())))
            {
                Symbol coerce = getCoerceToInt(rtype);
                if (coerce != null) {
                    right = gen.mkApply__(gen.Select(right, coerce));
                    rtype = defs.INT_TYPE();
                }
            }
        Symbol eqsym = getEqEq(ltype, rtype);
        return gen.mkApply_V(gen.Select(left, eqsym), new Tree[]{right});
    }

    protected Tree ThrowMatchError(int pos, Type type) {
        return gen.mkApplyTV(
			     gen.mkGlobalRef(pos, defs.MATCHERROR_FAIL()),
                             new Tree[]{gen.mkType(pos, type)},
                             new Tree[]{
                                 gen.mkStringLit(pos, unit.toString()),
                                 gen.mkIntLit(pos, Position.line(pos))
                             });
    }

    protected Tree ThrowMatchError(int pos, Type type, Tree tree) {
        return gen.mkApplyTV(
			     gen.mkGlobalRef(pos, defs.MATCHERROR_REPORT()),
                             new Tree[]{gen.mkType(pos, type)},
                             new Tree[]{
                                 gen.mkStringLit(pos, unit.toString()),
                                 gen.mkIntLit(pos, Position.line(pos)),
                                 tree
                             });
    }

    protected Tree Error(int pos, Type type) {
        return gen.mkApplyTV(
			     gen.mkGlobalRef(pos, defs.MATCHERROR_FAIL()),
                             new Tree[]{gen.mkType(pos, type)},
                             new Tree[]{
                                 gen.mkStringLit(pos, unit.toString()),
                                 gen.mkIntLit(pos, Position.line(pos))
                             });
    }


    Type pairType( Type left, Type right ) {
	return defs.TUPLE_TYPE(new Type[] { left, right } );
    }

    Tree newPair( Tree left, Tree right ) {
 	return gen.New(gen.mkApplyTV( gen.mkPrimaryConstructorGlobalRef( pos, defs.TUPLE_CLASS[2]),
                                                                   new Type[] { left.getType(), right.getType() },
                                                                   new Tree[] { left, right }));

    }

}
