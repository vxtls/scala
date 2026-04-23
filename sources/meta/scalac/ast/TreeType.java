/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

import meta.java.Type;

/** This class describes types used in tree nodes. */
public abstract class TreeType extends Type {

    //########################################################################
    // Private Constants

    private static final String NAME_PACKAGE  = "scalac.util";
    private static final String NAME_NAME     = "Name";
    private static final String NAME_FULLNAME = NAME_PACKAGE + "." + NAME_NAME;
    private static final String TREE_PACKAGE  = "scalac.ast";
    private static final String TREE_NAME     = "Tree";
    private static final String TREE_FULLNAME = TREE_PACKAGE + "." + TREE_NAME;

    //########################################################################
    // Public Constructors

    protected TreeType() {
    }

    //########################################################################
    // Public Factories

    public static Name Name(TreeKind kind) {
        return new Name(kind);
    }

    public static Tree Tree(TreeKind kind) {
        return new Tree(kind);
    }

    public static Node Node(TreeNode node) {
        return new Node(node);
    }

    //########################################################################
    // Public Methods

    /** Returns the type's (possibly fully qualified) name. */
    public String getName(boolean qualified) {
        if (this instanceof Name) {
            return qualified ? NAME_FULLNAME : NAME_NAME;
        }
        if (this instanceof Tree) {
            return qualified ? TREE_FULLNAME : TREE_NAME;
        }
        if (this instanceof Node) {
            TreeNode node = ((Node)this).node;
            return qualified ? TREE_FULLNAME + "." + node.name : node.name;
        }
        return super.getName(qualified);
    }

    /** Returns the type's owner (its package or enclosing type). */
    public String getOwner() {
        if (this instanceof Name) {
            return NAME_PACKAGE;
        }
        if (this instanceof Tree) {
            return TREE_PACKAGE;
        }
        if (this instanceof Node) {
            return TREE_FULLNAME;
        }
        return super.getOwner();
    }

    //########################################################################
    // Public Classes

    public static final class Name extends TreeType {
        public final TreeKind kind;

        private Name(TreeKind kind) {
            this.kind = kind;
        }
    }

    public static final class Tree extends TreeType {
        public final TreeKind kind;

        private Tree(TreeKind kind) {
            this.kind = kind;
        }
    }

    public static final class Node extends TreeType {
        public final TreeNode node;

        private Node(TreeNode node) {
            this.node = node;
        }
    }

    //########################################################################
}
