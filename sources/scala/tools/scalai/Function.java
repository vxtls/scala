/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Function.java,v 1.5 2002/07/01 13:16:39 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import scalac.symtab.Symbol;
import scalac.util.Debug;

public abstract class Function {

    //########################################################################
    // Public Cases

    public static final class Global extends Function {
        public final CodePromise code;

        private Global(CodePromise code) {
            this.code = code;
        }
    }

    public static final class Member extends Function {
        public final Symbol symbol;

        private Member(Symbol symbol) {
            this.symbol = symbol;
        }
    }

    public static final class Label extends Function {
        public final Symbol symbol;

        private Label(Symbol symbol) {
            this.symbol = symbol;
        }
    }

    public static final class JavaConstructor extends Function {
        public final Constructor constructor;

        private JavaConstructor(Constructor constructor) {
            this.constructor = constructor;
        }
    }

    public static final class JavaMethod extends Function {
        public final Method method;

        private JavaMethod(Method method) {
            this.method = method;
        }
    }

    public static final class Pos extends Function {
        private Pos() {
        }
    }
    public static final Pos Pos = new Pos();

    public static final class Neg extends Function {
        private Neg() {
        }
    }
    public static final Neg Neg = new Neg();

    public static final class Throw extends Function {
        private Throw() {
        }
    }
    public static final Throw Throw = new Throw();

    public static final class StringPlus extends Function {
        private StringPlus() {
        }
    }
    public static final StringPlus StringPlus = new StringPlus();

    public static final class EqEq extends Function {
        private EqEq() {
        }
    }
    public static final EqEq EqEq = new EqEq();

    public static final class BangEq extends Function {
        private BangEq() {
        }
    }
    public static final BangEq BangEq = new BangEq();

    public static final class HashCode extends Function {
        private HashCode() {
        }
    }
    public static final HashCode HashCode = new HashCode();

    public static final class ToString extends Function {
        private ToString() {
        }
    }
    public static final ToString ToString = new ToString();

    //########################################################################
    // Public Factory Methods

    public static Global Global(CodePromise code) {
        return new Global(code);
    }

    public static Member Member(Symbol symbol) {
        return new Member(symbol);
    }

    public static Label Label(Symbol symbol) {
        return new Label(symbol);
    }

    public static JavaConstructor JavaConstructor(Constructor constructor) {
        return new JavaConstructor(constructor);
    }

    public static JavaMethod JavaMethod(Method method) {
        return new JavaMethod(method);
    }

    //########################################################################
    // Public Methods

    public String toString() {
        if (this instanceof Global) {
            return "Global(" + ((Global)this).code + ")";
        }
        if (this instanceof Member) {
            return "Member(" + Debug.show(((Member)this).symbol) + ")";
        }
        if (this instanceof Label) {
            return "Label(" + Debug.show(((Label)this).symbol) + ")";
        }
        if (this instanceof JavaMethod) {
            return "JavaMethod(" + ((JavaMethod)this).method + ")";
        }
        if (this instanceof JavaConstructor) {
            return "JavaConstructor(" + ((JavaConstructor)this).constructor + ")";
        }
        if (this == Pos) {
            return "Pos";
        }
        if (this == Neg) {
            return "Neg";
        }
        if (this == Throw) {
            return "Throw";
        }
        if (this == StringPlus) {
            return "StringPlus";
        }
        if (this == EqEq) {
            return "EqEq";
        }
        if (this == BangEq) {
            return "BangEq";
        }
        if (this == HashCode) {
            return "HashCode";
        }
        if (this == ToString) {
            return "ToString";
        }
        throw Debug.abort("illegal function", this);
    }

    //########################################################################
}
