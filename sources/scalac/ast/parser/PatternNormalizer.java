/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002-2003, LAMP/EPFL         **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.ast.parser;

import scalac.Unit;
import scalac.ast.*;
import scalac.util.Name;
import scalac.ast.Tree.*;
import java.util.HashMap;

import scalac.util.Names;

/** contains algorithms for `checking' and `normalizing' patterns
 *
 *  @author  Burak Emir
 *  @version 1.0
 */

public class PatternNormalizer {

    /** the compilation unit - needed for error reporting
     */
    Unit unit;

    /** the tree factory
     */
    public TreeFactory make;

    // constructor ... unit is needed to display errors

    public PatternNormalizer( Unit unit ) {
	this.unit = unit;
        this.make = unit.global.make;
    }

    //
    ///////// CHECKING patterns for well-formedness ///////////////////////////////
    //

    int seqDepth;

    /** checks whether TO DO TO DO TO DO
     *  - @-recursion occurs only at the end just below a sequence
     *  returns true if the tree is ok.
     *  inSeq: flag, true if we are in a sequence '[' ... ']'
     *  t: the tree to be checked
     */
    protected boolean check1( Tree t, boolean inAlt ) {
	if (t instanceof Literal) {
	    return true;
	} else if (t instanceof Apply) {
	    Tree[] args = ((Apply)t).args;
            seqDepth++;
            boolean res = check1( args, inAlt );
            seqDepth--;
            return res;
	} else if (t instanceof Sequence) { // this is a hack to disallow deep binding
	    Tree[] trees = ((Sequence)t).trees;
            seqDepth++;
            boolean res = check1( trees, inAlt );
            seqDepth--;
            return res;
	} else if (t instanceof Alternative) {
	    Tree[] trees = ((Alternative)t).trees;
	    return check1( trees, true );
	} else if (t instanceof Bind) {
	    Bind bind = (Bind)t;
	    Name var = bind.name;
	    Tree tree = bind.rhs;
	    if(( inAlt )
	       &&( var.toString().lastIndexOf("$") == -1)) {

		unit.error( t.pos,
			      "variable binding not allowed under alternative");
		return false;
	    }
            if(( seqDepth > 2 )
               &&( var.toString().lastIndexOf("$") == -1)) {
		unit.error( t.pos,
			      "sorry, deep binding not implemented");
		return false;
            }
	    this.boundVars.put( var /*t.symbol()*/, Boolean.FALSE );
	    /*
              boolean result = check( tree, inSeq );
              if(((Boolean) this.boundVars.get( t.symbol() ))
	      .booleanValue()) { // occurs recursively
	      // do something to RESTRICT recursion
              }
	    */
	    return check1( tree, inAlt );
	} else if (t instanceof Typed) {
	    return true;
	} else if (t instanceof Ident) {
	    Name var = ((Ident)t).name;
	    if (inAlt && var.isVariable() && var != Names.PATTERN_WILDCARD &&
		var.lastPos((byte)'$') == -1) {
		unit.error( t.pos,
			    "variable not allowed under alternative");
		return false;
	    }
              /*
              System.out.println( t.symbol().toString() );
              */
	    if(( this.boundVars.containsKey( var /*t.symbol()*/ ))
	       &&( var.toString().lastIndexOf("$") == -1)) {
		  unit.error( t.pos,
			      "recursive patterns not allowed");

		  //this.boundVars.put( t.symbol(), Boolean.TRUE ); //mark recursive
                    //System.out.println( t.symbol() + "occurs recursively");
              }

	    return true;
	} else if (t instanceof Select) {
	    return true;
	} else {
	    unit.error( t.pos, "whut'z dis ?"+t.toString()); // never happens
	    return false;
	}

    }

    /** checkPat for every tree in array of trees, see below
     */
      protected boolean check1( Tree[] trees, boolean inAlt ) {
	for( int i = 0; i < trees.length; i++ )
	    if( !check1(  trees[ i ], inAlt ))
		return false;
	return true;
    }

      // if this map contains a symbol as a key, it is bound.
      // if this symbol is mapped to Boolean.True, it occurs recursively
    HashMap/*Name=>Boolean*/ boundVars;

      /**  method
       */
      public boolean check( Tree pat ) {
            this.boundVars = new HashMap();
            if( TreeInfo.isRegularPattern( pat ) )
                  seqDepth = 0;
            else
                  seqDepth = -32;  // don't care about sequences. see above
            return check1( pat, false );
    }


    //
    /////////////// NORMALIZING patterns /////////////////////////////////////////////////////////////
    //

    boolean isEmptySequence( Tree tree ) {
	if (tree instanceof Sequence) {
	    Tree[] trees = ((Sequence)tree).trees;
	    //return ((trees.length == 1)&&( trees[ 0 ] == Tree.Empty ));
	    return trees.length == 0;
	} else {
	    return false;
	}
    }

    boolean isSequence( Tree tree ) {
	if (tree instanceof Sequence) {
	    return true;
	} else {
	    return false;
	}
    }


    /** appends every non-empty tree in trees to ts
     */
    void appendNonEmpty( TreeList ts, Tree[] trees ) {
	for( int i = 0; i < trees.length; i++ )
	    if( !isEmptySequence( trees[ i ] ) )
		ts.append( trees[ i ] );
    }
    /** appends tree to ts if it is non-empty, leaves ts unchanged.
     */
    void appendNonEmpty( TreeList ts, Tree tree ) {
	//if( tree != Tree.Empty )
	if ( !isEmptySequence(tree))
	    ts.append( tree );
    }

    /** takes a (possibly empty) TreeList and make a Sequence out of it
     */
    Tree treeListToSequence( int pos, TreeList ts ) {
	//if( ts.length() == 0 ) // artefact of old version, where empty sequence was Subsequence node. delete soon
	//return make.Sequence( pos, Tree.EMPTY_ARRAY);
	return make.Sequence( pos, ts.toArray() );
    }



    /** (1) nested `Alternative' nodes are flattened (( tree traversal with clique contraction ))
     *       if all branches are empty, empty subsequence is returned.
     *       variables x are replaced by bindings x @ _
     */

    // apply `flattenAlternative' to each tree in ts
    public Tree[] flattenAlternatives( Tree[] ts ) {
	Tree[] res = new Tree[ ts.length ];
	for( int i = 0; i < ts.length; i++ )
	    res[ i ] = flattenAlternative( ts[ i ] );
	return res;
    }

    // main algo for (1)
    public Tree flattenAlternative( Tree tree ) {
	if (tree instanceof Alternative) {
	    Tree[] choices = ((Alternative)tree).trees;
	    TreeList cs = new TreeList();
	    for( int i = 0; i < choices.length; i++ ) {
		Tree child = choices[ i ];
		if (child instanceof Alternative) {
		    Tree[] child_choices = ((Alternative)child).trees;
		    cs.append( flattenAlternativeChildren( child_choices ) );
		} else {
		    cs.append( child );
		}
	    }
	    Tree[] newtrees = cs.toArray();
	    switch( newtrees.length ) {
	    case 1:
		return newtrees[ 0 ];
	    case 0:
		return make.Sequence( tree.pos, Tree.EMPTY_ARRAY );
		    default:
			return make.Alternative( tree.pos, cs.toArray() );
		    }

		    // recursive call
	} else if (tree instanceof Sequence) {
	    Tree[] trees = ((Sequence)tree).trees;
	    return make.Sequence( tree.pos, flattenAlternatives( trees ));
	} else if (tree instanceof Bind) {
	    Bind bind = (Bind)tree;
	    Name var = bind.name;
	    Tree body = bind.rhs;
	    return make.Bind( tree.pos, var, flattenAlternative( body ));
	} else {
	    return tree; // no alternatives can occur
	}
    }

    // main algo for (1), precondition: choices are children of an Alternative node
    public TreeList flattenAlternativeChildren( Tree[] choices ) {
	boolean allEmpty = true;
	TreeList cs = new TreeList();
	for( int j = 0; j < choices.length; j++ ) {
	    Tree tree = flattenAlternative( choices[ j ] ); // flatten child
	    if (tree instanceof Alternative) {
		Tree[] child_choices = ((Alternative)tree).trees;
		int tmp = cs.length();
		appendNonEmpty( cs, child_choices );
		if( cs.length() != tmp )
		    allEmpty = false;
	    } else {
		cs.append( tree );
		allEmpty = allEmpty && TreeInfo.isEmptySequence( tree );
	    }
	}
	if( allEmpty ) {
	    cs.clear();
	    cs.append(make.Sequence(choices[0].pos, Tree.EMPTY_ARRAY));
	}
	return cs;
    }



    /** (2) nested `Sequence' nodes are flattened (( clique elimination ))
     *      nested empty subsequences get deleted
     */

    // apply `flattenSequence' to each tree in trees
    public Tree[] flattenSequences( Tree[] trees ) {
	Tree[] res = new Tree[ trees.length ];
	for( int i = 0; i < trees.length; i++ )
	    res[ i ] = flattenSequence( trees[ i ] );
	return res;
    }
    // main algo for (2)
    public Tree flattenSequence( Tree tree ) {
	//System.out.println("flattenSequence of "+tree);
	if (tree instanceof Sequence) {
	    Tree[] trees = ((Sequence)tree).trees;
	    /*
		case Sequence( Tree[] trees ):
		    trees = flattenSequences( trees );
	    if(( trees.length == 1 )&&( isEmptySequence( trees[ 0 ] )))
		trees = Tree.EMPTY_ARRAY;
		    return make.Sequence( tree.pos, trees );
		    */
	    TreeList ts = new TreeList();
	    for( int i = 0; i < trees.length; i++ ) {
		Tree child = trees[ i ];
		if (child instanceof Sequence) {
		    Tree[] child_trees = ((Sequence)child).trees;
		    ts.append( flattenSequenceChildren( child_trees ) );
		} else {
		    ts.append( child );
		}
	    }
	    /*
	      System.out.print("ts = ");
	    for(int jj = 0; jj<ts.length(); jj++) {
		System.out.print(ts.get( jj ).toString()+" ");
	    }
	    System.out.println();
		    */
	    return treeListToSequence( tree.pos, ts ) ;

		    // recursive call
	} else if (tree instanceof Alternative) {
	    Tree[] choices = ((Alternative)tree).trees;
	    return make.Alternative( tree.pos, flattenSequences( choices ));
	} else if (tree instanceof Bind) {
	    Bind bind = (Bind)tree;
	    Name var = bind.name;
	    Tree body = bind.rhs;
	    return make.Bind( tree.pos, var, flattenSequence( body ));
	} else {
	    return tree;
	}
    }

    // main algo for (2), precondition: trees are children of a Sequence node
    public TreeList flattenSequenceChildren( Tree[] trees ) {
	TreeList ts = new TreeList();
	for( int j = 0; j < trees.length; j++ ) {
	    Tree tree = flattenSequence( trees[ j ] );
	    if (tree instanceof Sequence) {
		Tree[] child_trees = ((Sequence)tree).trees;
		appendNonEmpty( ts, child_trees );
	    } else {
		appendNonEmpty( ts, tree );
	    }
	}
	return ts;
    }


    /** (3) in `Sequence':
     *             children of direct successor nodes labelled `Sequence' are moved up
     *      (( tree traversal, left-to-right traversal ))
     */

    /** applies `elimSequence' to each tree is ts
     */
    public Tree[] elimSequences( Tree[] ts ) {
	Tree[] res = new Tree[ ts.length ];
	for( int i = 0; i < ts.length; i++ )
	    res[ i ] = elimSequence( ts[ i ] );
	return res;
    }

    public Tree elimSequence( Tree tree ) {
	if (tree instanceof Sequence) {
	    Tree[] trees = ((Sequence)tree).trees;
	    // might be empty ...
	    Tree[] newtrees = mergeHedge( trees ).toArray();
	    if(( newtrees.length == 1 )&&( isEmptySequence( newtrees[ 0 ] )))
		return make.Sequence( tree.pos, Tree.EMPTY_ARRAY );
	    return make.Sequence( tree.pos, newtrees );

	    // recurse
	    //case Sequence( Tree[] trees ): // after normalization (2), Sequence is flat
	    /*
	    TreeList ts = new TreeList();
	    for( int i = 0; i < trees.length; i++ ) {
		Tree t = trees[ i ];
		//if( t != Tree.Empty )                          // forget empty subsequences
		    ts.append( elimSequence( t )); // recurse
	    }
	    return treeListToSequence( tree.pos, ts );
		    */
	    //return make.Sequence( tree.pos, elimSequences( trees ));
	} else if (tree instanceof Alternative) {
	    Tree[] choices = ((Alternative)tree).trees;
	    Tree result = make.Alternative( tree.pos, elimSequences( choices ) );
	    return flattenAlternative( result ); // apply
	} else if (tree instanceof Bind) {
	    Bind bind = (Bind)tree;
	    Name var = bind.name;
	    Tree body = bind.rhs;
	    return make.Bind( tree.pos, var, elimSequence( body ));
	} else {
	    return tree; // nothing to do
	}
    }

    /** runs through an array of trees, merging adjacent `Sequence' nodes if possible
     *  returns a (possibly empty) TreeList
     *  precondition: trees are children of a Sequence node
     */
    TreeList mergeHedge( Tree[] trees ) {
	    TreeList ts = new TreeList();
	    if( trees.length > 1 ) {    // more than one child
		Tree left  = trees[ 0 ];
		Tree right = null;;
		Tree merge = null;
		for( int i = 0; i < trees.length - 1; i++) {
		    right = trees[ i+1 ];
		    merge = mergeThem( left, right );
		    if( merge != null ) {
			left = merge;
		    } else {
			ts.append( left );
			left = right;
		    }
		}
		if( merge!= null ) {
		    ts.append( merge );
		} else {
		    if( right != null )
			ts.append( right );
		}
	    } else if ( trees.length == 1 ) {
                  //if( trees[ 0 ] != Tree.Empty )
		    ts.append( trees[ 0 ] ); // append the single child
	    }
	    return ts;
    }

    /** if either left or right are subsequences, returns a new subsequences node
     *  with the children of both, where any occurences of Tree.Empty are removed
     *  otherwise, returns null. "move concatenation to the top"
     */
    Tree mergeThem( Tree left, Tree right ) {
	if (left instanceof Sequence) {             // left tree is subsequence
	    Tree[] treesLeft = ((Sequence)left).trees;
	    TreeList ts = new TreeList();
	    appendNonEmpty( ts, treesLeft );
	    if (right instanceof Sequence) {
		Tree[] treesRight = ((Sequence)right).trees;
		appendNonEmpty( ts, treesRight ); // ...and right tree is subsequence
	    } else {
		ts.append( right );                       // ...and right tree is atom
	    }
	    return treeListToSequence( left.pos, ts );
	} else {                                          // left tree is atom
	    if (right instanceof Sequence) {
		Tree[] treesRight = ((Sequence)right).trees;
		TreeList ts = new TreeList();
		ts.append( left );
		appendNonEmpty( ts, treesRight ); // ...and right tree is subsequence
		return treeListToSequence( left.pos, ts );
	    }
	}
	return null;                                      // ...and right tree is atom -- no merge
    }



    /* (4) If `Alternative' at least one subsequence branch, then we wrap every atom that may
     *        occur in a `Sequence' tree
     *       (( tree traversal ))
     */

    /** applies `warpAlternative' to each tree is ts
     */
    public Tree[] wrapAlternatives( Tree[] trees ) {
	Tree[] newts = new Tree[ trees.length ];
	for( int i = 0; i < trees.length; i++ )
	    newts[ i ] = wrapAlternative( trees[ i ] );
	return newts;
    }

    /** main algo for (4)
     */
     public Tree wrapAlternative( Tree tree ) {
            if (tree instanceof Alternative) {
                  Tree[] choices = ((Alternative)tree).trees;
	                  return make.Alternative( tree.pos, wrapAlternativeChildren( choices ));
	                  // recursive
            } else if (tree instanceof Sequence) {
                  Tree[] trees = ((Sequence)tree).trees;
	                  return make.Sequence( tree.pos, wrapAlternatives( trees ));
            } else if (tree instanceof Bind) {
                  Bind bind = (Bind)tree;
                  Name var = bind.name;
                  Tree body = bind.rhs;
	                  return make.Bind( tree.pos, var, wrapAlternative( body ));

            } else if (tree instanceof Ident) {
                  Name name = ((Ident)tree).name;
	                  /*
	                  System.out.println( "in case Ident, name" +name);
	                  if ( name != Name.fromString("_")
                       && ( boundVars.get( tree.symbol() ) == null )) {

                        System.out.println("TRANSF, name:"+name);

                        return make.Bind( tree.pos,
                                          name,
                                          make.Ident( tree.pos,
                                                      Name.fromString("_") ))
                              .symbol( tree.symbol() )
	                              .type( tree.type );
	                  }
	                  */
	              return tree;

	    } else {
	        return tree;
	    }
    }

    /** algo for (4), precondition: choices are direct successors of an `Alternative' node
     */
    Tree[] wrapAlternativeChildren( Tree[] choices ) {
	Tree[] newchoices = new Tree[ choices.length ];


	for( int i = 0; i < choices.length; i++ )
	    newchoices[ i ] = wrapAlternative( choices[ i ] ); // recursive call

	if( hasSequenceBranch( newchoices ))
	    for( int i = 0; i < choices.length; i++ )
		newchoices[ i ] = wrapElement( choices[ i ] );
	return newchoices;
    }

    /** returns true if at least one tree in etrees is a subsequence value
     */
    boolean hasSequenceBranch( Tree[] trees ) {
	boolean isSubseq = false;
	for( int i = 0; i < trees.length && !isSubseq; i++ )
	    isSubseq |= isSequenceBranch( trees[ i ] );
	return isSubseq;
    }

    /*  returns true if the argument is a subsequence value, i.e. if
     *  is is labelled `Sequence' or  a `Bind' or an `Alternative' with a `Sequence' node below
     *  precondition: choices are in normal form w.r.t. to (4)
     */
    boolean isSequenceBranch( Tree tree ) {
	if (tree instanceof Sequence) {
	    return true;
	} else if (tree instanceof Alternative) { // normal form -> just check first child
	    Tree[] trees = ((Alternative)tree).trees;
	    return trees[ 0 ] instanceof Sequence;
	} else if (tree instanceof Bind) {
	    Tree body = ((Bind)tree).rhs;
	    return isSequenceBranch( body );
	} else {
	    return false;
	}
    }

    Tree wrapElement( Tree tree ) {
	if (tree instanceof Sequence) {
	    return tree;
	} else {
	    return make.Sequence(tree.pos, new Tree[] { tree } );
	}
    }

}
