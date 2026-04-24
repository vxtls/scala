/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

package scala.runtime;

public class ResultOrException<A> {

    public A result;

    public Throwable exc;

    ResultOrException(A result, Throwable exc) {
	this.result = result;
	this.exc = exc;
    }

    public static <A> ResultOrException<A> tryBlock(scala.Function0<A> block) {
	try {
	    return new ResultOrException<A>(block.apply(), null);
	} catch (Throwable ex) {
	    return new ResultOrException<A>(null, ex);
	}
    }
}
