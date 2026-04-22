/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $OldId: Ref.java,v 1.2 2002/03/12 13:16:04 zenger Exp $
// $Id$

package scala;

public class Ref<T> extends java.lang.Object {

    public T elem = null;

    public Ref(T x) {
	elem = x;
    }
}
