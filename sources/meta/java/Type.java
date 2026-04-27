/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.java;

/** A representation for Java types. */
public abstract class Type {

    //########################################################################
    // Public Constants

    /** The Java primitive type void */
    public static final Primitive VOID    = Primitive("void");

    /** The Java primitive type boolean */
    public static final Primitive BOOLEAN = Primitive("boolean");

    /** The Java primitive type byte */
    public static final Primitive BYTE    = Primitive("byte");

    /** The Java primitive type short */
    public static final Primitive SHORT   = Primitive("short");

    /** The Java primitive type char */
    public static final Primitive CHAR    = Primitive("char");

    /** The Java primitive type int */
    public static final Primitive INT     = Primitive("int");

    /** The Java primitive type long */
    public static final Primitive LONG    = Primitive("long");

    /** The Java primitive type float */
    public static final Primitive FLOAT   = Primitive("float");

    /** The Java primitive type double */
    public static final Primitive DOUBLE  = Primitive("double");

    //########################################################################
    // Public Constructors

    protected Type() {
    }

    //########################################################################
    // Public Factories

    /** Creates a primitive type. */
    public static Primitive Primitive(String name) {
        return new Primitive(name);
    }

    /** Creates a reference type (the owner may be null). */
    public static Reference Reference(String owner, String name) {
        return new Reference(owner, name);
    }

    /** Creates an array type. */
    public static Array Array(Type item) {
        return new Array(item);
    }

    //########################################################################
    // Public Methods

    /** Returns the type's fully qualified name. */
    public String getFullName() {
        return getName(true);
    }

    /** Returns the type's short name. */
    public String getName() {
        return getName(false);
    }

    /** Returns the type's (possibly fully qualified) name. */
    public String getName(boolean qualified) {
        if (this instanceof Primitive) {
            return ((Primitive)this).name;
        }
        if (this instanceof Reference) {
            Reference type = (Reference)this;
            return qualified && type.owner != null ? type.owner + "." + type.name : type.name;
        }
        if (this instanceof Array) {
            return ((Array)this).item.getName(qualified) + "[]";
        }
        throw new Error("illegal case: " + getName(true));
    }

    /** Returns the type's owner (its package or enclosing type). */
    public String getOwner() {
        if (this instanceof Primitive) {
            return null;
        }
        if (this instanceof Reference) {
            return ((Reference)this).owner;
        }
        if (this instanceof Array) {
            return ((Array)this).item.getOwner();
        }
        throw new Error("illegal case: " + getName(true));
    }

    /** If this is an array type, returns the type of the elements. */
    public Type getItemType() {
        if (this instanceof Array) {
            return ((Array)this).item;
        }
        throw new Error("not an array type: " + getName(true));
    }

    /** Returns the base type of this type. */
    public Type getBaseType() {
        return isArray() ? getItemType() : this;
    }

    /** Returns true if this is a primitive type. */
    public boolean isPrimitive() {
        return this instanceof Primitive;
    }

    /** Returns true if this is an array type. */
    public boolean isArray() {
        return this instanceof Array;
    }

    /**
     * Returns the string representation of an array instantiation
     * with the given bounds and whose elements are of this type.
     */
    public String newArray(String bounds) {
        if (this instanceof Array) {
            return ((Array)this).item.newArray(bounds + "[]");
        }
        return this + bounds;
    }

    /** Returns the string representation of this type. */
    public String toString() {
        return getName();
    }

    //########################################################################
    // Public Classes

    /** A primitive type. */
    public static class Primitive extends Type {
        public final String name;

        private Primitive(String name) {
            this.name = name;
        }
    }

    /** A reference type. */
    public static class Reference extends Type {
        public final String owner;
        public final String name;

        private Reference(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }
    }

    /** An array type. */
    public static class Array extends Type {
        public final Type item;

        private Array(Type item) {
            this.item = item;
        }
    }

    //########################################################################
}
