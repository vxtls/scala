/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $OldId: Array.java,v 1.9 2002/03/18 16:55:10 zenger Exp $
// $Id$

package scala;

public abstract class Array<T>
    extends java.lang.Object
    implements Function1<scala.Int, T>, Cloneable, java.io.Serializable {

    public int length;

    /** @meta constr (scala.Int);
     */
    public Array() {
    }

    public boolean[] asBooleanArray() {
        throw new ClassCastException();
    }

    public byte[] asByteArray() {
        throw new ClassCastException();
    }

    public short[] asShortArray() {
        throw new ClassCastException();
    }

    public char[] asCharArray() {
        throw new ClassCastException();
    }

    public int[] asIntArray() {
        throw new ClassCastException();
    }

    public long[] asLongArray() {
        throw new ClassCastException();
    }

    public float[] asFloatArray() {
        throw new ClassCastException();
    }

    public double[] asDoubleArray() {
        throw new ClassCastException();
    }

    /** @meta method () scala.Array[scala.AnyRef];
     */
    public java.lang.Object[] asObjectArray() {
        throw new ClassCastException();
    }

    /** @meta method () scala.Array[?T];
     */
    public java.lang.Object asArray() {
        throw new ClassCastException();
    }

    public T apply(scala.Int i) {
	return apply(i.value);
    }

    public abstract T apply(int i);

    public abstract void update(int i, T x);

    public abstract int length();
}
