/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac.ast;

import meta.java.Type;

public class MetaTree extends AbstractTreeExpander {

    //########################################################################
    // Public Methods

    public void printEmptyArrays() {
        printEmptyArrays(tree.getType(0), "EMPTY", tree.arrays);
        for (int i = 0; i < tree.nodes.length; i++) {
            TreeNode node = tree.nodes[i];
            printEmptyArrays(node.getType(0), node + "_EMPTY", node.arrays);
        }
    }

    public void printEmptyArrays(Type base, String prefix, int maxRank) {
        Type type = base;
        for (int rank = 1; rank <= maxRank; rank++) {
            type = Type.Array(type);
            writer.print("public static final ").print(type).print(" "+prefix);
            for (int i = 0; i < rank; i++) writer.print("_ARRAY");
            writer.print(" = new ").print(base).print("[0]");
            for (int i = 1; i < rank; i++) writer.print("[]");
            writer.println(";");
        }
    }

    public void printTreeCases() {
        for (int i = 0; i < tree.nodes.length; i++)
            printTreeCase(tree.nodes[i]);
    }

    private void printTreeCase(TreeNode node) {
        writer.printDescription(new String[] {
            node.description,
            "- kind         : " + description(node.kind),
            "- symbol       : " + description(node.symbol),
            "- introduced by: " + node.start.name,
            "- eliminated by: " + node.stop.name,
        });
        if (node.fields == null) {
            writer.print("public static final class ").print(node.name)
                .print(" extends Tree").lbrace();
            writer.print("private ").print(node.name).print("()").lbrace();
            writer.rbrace();
            writer.rbrace();
            writer.print("public static final ").print(node.name).print(" ")
                .print(node.name).print(" = new ").print(node.name)
                .println("();");
        } else {
            writer.print("public static class ").print(node.name)
                .print(" extends Tree").lbrace();
            node.printFieldDecls(writer);
            if (node.fields.length > 0) writer.println();
            node.printDecl(writer.print("public "), null, false).lbrace();
            for (int i = 0; i < node.fields.length; i++) {
                writer.println("this." + node.fields[i].name + " = " +
                    node.fields[i].name + ";");
            }
            writer.println("assert CheckTreeNodes.instance.checkNode(this);");
            writer.rbrace();
            writer.rbrace();
        }
        if (node == tree.n_Empty)
            writer.print("static { " + node + ".type = Type.NoType; }");
        writer.println();
    }

    private String description(TreeKind kind) {
        if (kind == TreeKind.Any) {
            return "this tree is of any kind";
        }
        if (kind == TreeKind.Type) {
            return "this tree is a type";
        }
        if (kind == TreeKind.Term) {
            return "this tree is a term";
        }
        if (kind == TreeKind.Dual) {
            return "this tree is a type or a term";
        }
        if (kind == TreeKind.Test) {
            return "this tree is a type or a term " +
                   "(determined by the kind of the name field)";
        }
        if (kind == TreeKind.None) {
            return "this tree is neither a type nor a term";
        }
        throw new Error(kind.getClass().getName());
    }

    private String description(TreeSymbol symbol) {
        if (symbol == TreeSymbol.NoSym) {
            return "this tree has no symbol";
        }
        if (symbol instanceof TreeSymbol.HasSym) {
            return ((TreeSymbol.HasSym)symbol).isDef
                ? "this tree defines a symbol"
                : "this tree references a symbol";
        }
        throw new Error(symbol.getClass().getName());
    }

    public void printIsKind() {
        printIsKind(tree.nodes, TreeKind.Type);
        printIsKind(tree.nodes, TreeKind.Term);
    }

    private void printIsKind(TreeNode[] nodes, TreeKind kind) {
        writer.println("/** Returns true if this tree is a " +
            kind.toString().toLowerCase() + ". */");
        writer.print("public boolean is" + kind + "()").lbrace();
        boolean hasCase = false;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].kind == TreeKind.Test || !nodes[i].kind.isA(kind))
                continue;
            writer.print(hasCase ? "else if (" : "if (");
            nodes[i].printInstanceTest(writer, "this").println(")");
            writer.indent().println("return true;").undent();
            hasCase = true;
        }

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].kind != TreeKind.Test) continue;
            TreeField field = nodes[i].getTestField();
            writer.print(hasCase ? "else if (" : "if (");
            nodes[i].printInstanceTest(writer, "this").print(")").lbrace();
            writer.print(nodes[i].name).print(" node = (").print(nodes[i].name)
                .println(")this;");
            field.print(writer, true).print(" = node.").print(field.name)
                .println(";");
            writer.print("return ");
            if (kind == TreeKind.Type) {
                writer.print(field.name).print(".isTypeName() && (symbol() == null || !symbol().isConstructor()) || ")
                    .print(field.name).print(" == Name.ERROR");
            } else if (kind == TreeKind.Term) {
                writer.print(field.name).print(".isTermName() || (symbol() != null && symbol().isConstructor())");
            } else {
                throw new Error("unexpected kind " + kind);
            }
            writer.println(";");
            writer.rbrace();
            hasCase = true;
        }

        if (hasCase) writer.println("else");
        writer.indent().println("return false;").undent();
        writer.rbrace();
        writer.println();
    }

    public void printExtClasses() {
        for (int i = 0;i < tree.nodes.length;i++) printExtClass(tree.nodes[i]);
    }

    private void printExtClass(TreeNode node) {
        TreeField symbol = node.getSymbol();
        if (symbol == null) return;
        writer.print("public static class Ext"+node+" extends "+node).lbrace();
        symbol.print(writer.print("private "), true).println(";");
        writer.println();

        printExtConstructor(node, false);
        printExtConstructor(node, true);
        writer.println();

        writer.print("public boolean hasSymbol()").lbrace();
        writer.println("return true;");
        writer.rbrace();
        writer.println();

        if (node.definesSymbol()) {
            writer.print("public boolean definesSymbol()").lbrace();
            writer.println("return true;");
            writer.rbrace();
            writer.println();
        }

        writer.print("public ").print(symbol.type).print(" symbol()");
        writer.lbrace();
        writer.println("return " + symbol + ";");
        writer.rbrace();
        writer.println();

        writer.print("public ").print(tree.getType(0)).print(" setSymbol");
        symbol.print(writer.print("("), true).print(")").lbrace();
        printSetSymbol(symbol);
        for (int i = 0; i < node.fields.length; i++) {
            TreeField field = node.fields[i];
            TreeFieldLink link = field.link;
            if (link == null) continue;
            link.print(writer.print("this."+field+" = "),symbol).println(";");
        }
        writer.println("return this;");
        writer.rbrace();

        writer.rbrace();
        writer.println();
    }

    private void printExtConstructor(TreeNode node, boolean withSymbol) {
        node.printDecl(writer.print("public Ext"), null, withSymbol).lbrace();
        TreeField symbol = node.getSymbol();
        writer.print("super(");
        for (int i = 0; i < node.fields.length; i++) {
            if (i > 0) writer.print(", ");
            if (withSymbol && node.fields[i].link != null) {
                node.fields[i].link.print(writer, symbol);
            } else {
                writer.print(node.fields[i].name);
            }
        }
        writer.println(");");
        if (withSymbol) printSetSymbol(symbol);
        writer.rbrace();
    }

    private void printSetSymbol(TreeField symbol) {
        writer.println("assert " + symbol + " != null : \"null symbol\";");
        writer.println("this." + symbol + " = " + symbol + ";");
    }

    public void printEmptyCases() {
        for (int i = 0;i < tree.nodes.length;i++) {
            tree.nodes[i].printCase(writer, false).println();
            writer.println();
        }
    }

    //########################################################################
}
