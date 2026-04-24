/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
**                                                                      **
** $Id$
\*                                                                      */

package scalac.transformer.matching;

import scalac.Global;
import scalac.ast.*;
import scalac.util.*;
import scalac.symtab.*;
import java.util.*;


public class TestRegTraverser extends Traverser {
    boolean result = false;
    Set variables = new HashSet();
    static Set nilVariables = new HashSet();

    public void traverse(Tree tree) {
    	if (!result)
	    if (tree instanceof Tree.Alternative) {
		result = true;
	    } else if (tree instanceof Tree.Bind) {
                Tree pat = ((Tree.Bind)tree).rhs;
		if( TreeInfo.isEmptySequence( pat ) ) {
		    // annoying special case: b@() [or b@(()|()) after normalization]
		    //System.err.println("bindin empty "+tree.symbol());
		    nilVariables.add(tree.symbol());
                    result = true;
		} else {
		    variables.add(tree.symbol());
		}
		traverse(pat);
	    } else if (tree instanceof Tree.Ident) {
		Symbol symbol = tree.symbol();
		if ((symbol != Global.instance.definitions.PATTERN_WILDCARD) &&
		    variables.contains(symbol))
		    result = true;
	    } else if (tree instanceof Tree.CaseDef) {
                Tree pat = ((Tree.CaseDef)tree).pat;
		traverse(pat);
	    } else if (tree instanceof Tree.Sequence) {
                Tree[] trees = ((Tree.Sequence)tree).trees;
		//result = true;
		traverse( trees );
		//result = true;
	    } else {
		super.traverse( tree );
	    }
    }

    public static boolean apply(Tree t) {
        TestRegTraverser trt = new TestRegTraverser();
        nilVariables.clear();
        trt.traverse(t);
        //System.err.println("TestRegTraverser says "+t+" -> "+trt.result);
        return trt.result;
    }

    public static Set getNilVariables() {
	return nilVariables;
    }
}
