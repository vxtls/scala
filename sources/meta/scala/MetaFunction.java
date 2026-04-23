/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scala;

import meta.java.AbstractJavaExpander;

public class MetaFunction extends AbstractJavaExpander {

    //########################################################################
    // Public Fields

    public final int arity;

    //########################################################################
    // Public Constructors

    public MetaFunction(int arity) {
        this.arity = arity;
    }

    //########################################################################
    // Public Methods

    public String getTargetBaseName() {
        return super.getTargetBaseName() + arity;
    }

    public void printn() {
        writer.print(arity);
    }

    public void printClassScalaTParams() {
        for (int i = 0; i < arity; i++)
            writer.print("- ?A").print(i).print(", ");
        writer.print("+ ?R");
    }

    public void printClassJavaTParams() {
        if (arity > 0) {
            writer.print("<");
            for (int i = 0; i < arity; i++) {
                if (i > 0) writer.print(", ");
                writer.print("A").print(i);
            }
            writer.print(", R>");
        } else {
            writer.print("<R>");
        }
    }

    public void printApplyScalaSignature() {
        writer.print("(");
        for (int i = 0; i < arity; i++)
            writer.print("?A").print(i).print(", ");
        writer.print(") ?R");
    }

    public void printApplyJavaSignature() {
        writer.print("R apply(");
        for (int i = 0; i < arity; i++) {
            if (i > 0) writer.print(", ");
            writer.print("A").print(i).print(" a").print(i);
        }
        writer.print(")");
    }

    public void printApplyJavaVParams() {
        for (int i = 0; i < arity; i++) {
            if (i > 0) writer.print(", ");
            writer.print("java.lang.Object a").print(i);
        }
    }

    //########################################################################
}
