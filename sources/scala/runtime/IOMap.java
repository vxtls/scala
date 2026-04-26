/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

package scala.runtime;

/**
 * Purely functional maps from integers to objects. Implemented as
 * red-black trees.
 *
 * @author Michel Schinz
 * @version 1.0
 */

public class IOMap {

    /** The map class itself */
    public static abstract class T {
        private T() {}

        public static final class N extends T {
            public final int c;
            public final T l;
            public final T r;
            public final int k;
            public final Object v;

            public N(int c, T l, T r, int k, Object v) {
                this.c = c;
                this.l = l;
                this.r = r;
                this.k = k;
                this.v = v;
            }
        }

        public static final class E extends T {
            private E() {}
        }

        public static T N(int c, T l, T r, int k, Object v) {
            return new N(c, l, r, k, v);
        }

        public static final T E = new E();
    }

    public static final T EMPTY = T.E;

    // Node colors (Black and Red)
    private static final int B = 0;
    private static final int R = 1;

    public static class ConflictException extends Exception {
        public final int key;
        public final Object oldValue, newValue;

        public ConflictException(int key, Object oldValue, Object newValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        public Throwable fillInStackTrace() {
            // do nothing, to speed up things
            return this;
        }
    }

    public Object resolveConflict(int k, Object oldV, Object newV)
        throws ConflictException {
        throw new ConflictException(k, oldV, newV);
    }

    public T put(T map, int key, Object value) throws ConflictException {
        T result = putAux(map, key, value);
        if (result instanceof T.N) {
            T.N node = (T.N)result;
            return T.N(B, node.l, node.r, node.k, node.v);
        }
        throw new Error();
    }

    private T putAux(T map, int key, Object value) throws ConflictException {
        if (map instanceof T.N) {
            T.N node = (T.N)map;
            if (key < node.k)
                return balance(T.N(node.c, putAux(node.l, key, value), node.r, node.k, node.v));
            else if (key > node.k)
                return balance(T.N(node.c, node.l, putAux(node.r, key, value), node.k, node.v));
            else
                return T.N(node.c, node.l, node.r, node.k, resolveConflict(node.k, node.v, value));
        } else if (map instanceof T.E) {
            return T.N(R, T.E, T.E, key, value);
        }
        throw new Error();
    }

    private T balance(T t) {
        if (!(t instanceof T.N)) {
            return t;
        }
        T.N node = (T.N)t;
        if (node.c != B) {
            return t;
        }

        if (node.l instanceof T.N) {
            T.N left = (T.N)node.l;
            if (left.c == R) {
                if (left.l instanceof T.N) {
                    T.N leftLeft = (T.N)left.l;
                    if (leftLeft.c == R) {
                        return T.N(R,
                                   T.N(B, leftLeft.l, leftLeft.r, leftLeft.k, leftLeft.v),
                                   T.N(B, left.r, node.r, node.k, node.v),
                                   left.k,
                                   left.v);
                    }
                }
                if (left.r instanceof T.N) {
                    T.N leftRight = (T.N)left.r;
                    if (leftRight.c == R) {
                        return T.N(R,
                                   T.N(B, left.l, leftRight.l, left.k, left.v),
                                   T.N(B, leftRight.r, node.r, node.k, node.v),
                                   leftRight.k,
                                   leftRight.v);
                    }
                }
            }
        }

        if (node.r instanceof T.N) {
            T.N right = (T.N)node.r;
            if (right.c == R) {
                if (right.l instanceof T.N) {
                    T.N rightLeft = (T.N)right.l;
                    if (rightLeft.c == R) {
                        return T.N(R,
                                   T.N(B, node.l, rightLeft.l, node.k, node.v),
                                   T.N(B, rightLeft.r, right.r, right.k, right.v),
                                   rightLeft.k,
                                   rightLeft.v);
                    }
                }
                if (right.r instanceof T.N) {
                    T.N rightRight = (T.N)right.r;
                    if (rightRight.c == R) {
                        return T.N(R,
                                   T.N(B, node.l, right.l, node.k, node.v),
                                   T.N(B, rightRight.l, rightRight.r, rightRight.k, rightRight.v),
                                   right.k,
                                   right.v);
                    }
                }
            }
        }

        return t;
    }

    public Object get(T map, int key) {
        if (map instanceof T.N) {
            T.N node = (T.N)map;
            if (key < node.k)
                return get(node.l, key);
            else if (key > node.k)
                return get(node.r, key);
            else
                return node.v;
        } else if (map instanceof T.E) {
            return null;
        }
        throw new Error("unexpected node " + this);
    }

    public int size(T map) {
        if (map instanceof T.N) {
            T.N node = (T.N)map;
            return size(node.l) + size(node.r) + 1;
        } else if (map instanceof T.E) {
            return 0;
        }
        throw new Error("unexpected node " + this);
    }

    public int depth(T map) {
        if (map instanceof T.N) {
            T.N node = (T.N)map;
            return Math.max(depth(node.l), depth(node.r)) + 1;
        } else if (map instanceof T.E) {
            return 0;
        }
        throw new Error("unexpected node " + this);
    }
}

// class RBTest {
//     static class MyIOMap extends IOMap {
//         public Object resolveConflict(int k, Object oldV, Object newV) {
//             throw new Error("conflict!!!");
//         }
//     }

//     public static void main(String[] args) {
//         MyIOMap map = new MyIOMap();
//         MyIOMap.T t = map.EMPTY;

//         long start = System.currentTimeMillis();
//         for (int i = 0; i < args.length; ++i) {
//             t = map.put(t, FNV_Hash.hash32(args[i]), new Integer(i));
//         }

//         for (int i = 0; i < args.length; ++i) {
//             map.get(t, FNV_Hash.hash32(args[i]));
//         }
//         long end = System.currentTimeMillis();
//         System.out.println("time: " + (end - start) + "ms");

//         System.out.println("size  = " + map.size(t));
//         System.out.println("depth = " + map.depth(t));
//     }
// }
