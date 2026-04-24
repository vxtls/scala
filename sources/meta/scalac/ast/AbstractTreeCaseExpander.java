/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

/** A base class for expanders that generate switches on tree nodes. */
public abstract class AbstractTreeCaseExpander extends AbstractTreeExpander {

    //########################################################################
    // Public Constructors

    public AbstractTreeCaseExpander() {
        writer.importType(t_Debug);
    }

    //########################################################################
    // Public Methods

    public void printTreeSwitch() {
        for (int i = 0; i < tree.nodes.length; i++) {
            printTreeCase(tree.nodes[i], i == 0);
            writer.println();
        }
        writer.print("else").lbrace();
        writer.print("throw ").print(t_Debug).
            println(".abort(\"unknown tree\", tree);");
        writer.rbrace();
    }

    public void printTreeCases() {
        for (int i = 0; i < tree.nodes.length; i++) {
            printTreeCase(tree.nodes[i], i == 0);
            writer.println();
        }
    }

    public void printTreeCase(TreeNode node, boolean first) {
        printTreeCaseHeader(node, first);
        writer.println();
        printTreeCaseBody(node);
        printTreeCaseFooter(node);
    }

    public void printTreeCaseHeader(TreeNode node, boolean first) {
        writer.print(first ? "if (" : "else if (");
        node.printInstanceTest(writer, "tree").print(")").lbrace();
        node.printExtractor(writer, "tree", false);
    }

    public abstract void printTreeCaseBody(TreeNode node);

    public void printTreeCaseFooter(TreeNode node) {
        writer.rbrace();
    }

    //########################################################################
}
