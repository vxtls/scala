/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Variable.java,v 1.4 2002/06/17 12:33:38 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.lang.reflect.Field;

import scalac.util.Debug;

public abstract class Variable {

    //########################################################################
    // Public Cases

    public static final class Global extends Variable {
        public Object value;

        private Global(Object value) {
            this.value = value;
        }
    }

    public static final class Module extends Variable {
        public ScalaTemplate template;
        public Object value;

        private Module(ScalaTemplate template, Object value) {
            this.template = template;
            this.value = value;
        }
    }

    public static final class Member extends Variable {
        public final int index;

        private Member(int index) {
            this.index = index;
        }
    }

    public static final class Argument extends Variable {
        public final int index;

        private Argument(int index) {
            this.index = index;
        }
    }

    public static final class Local extends Variable {
        public final int index;

        private Local(int index) {
            this.index = index;
        }
    }

    public static final class JavaField extends Variable {
        public final Field field;

        private JavaField(Field field) {
            this.field = field;
        }
    }

    //########################################################################
    // Public Factory Methods

    public static Global Global(Object value) {
        return new Global(value);
    }

    public static Module Module(ScalaTemplate template, Object value) {
        return new Module(template, value);
    }

    public static Member Member(int index) {
        return new Member(index);
    }

    public static Argument Argument(int index) {
        return new Argument(index);
    }

    public static Local Local(int index) {
        return new Local(index);
    }

    public static JavaField JavaField(Field field) {
        return new JavaField(field);
    }

    //########################################################################
    // Public Methods

    public String toString() {
        if (this instanceof Global) {
            return "Global(" + Debug.show(((Global)this).value) + ")";
        }
        if (this instanceof Module) {
            Module module = (Module)this;
            return "Module(" + module.template + "," + Debug.show(module.value) + ")";
        }
        if (this instanceof Member) {
            return "Member(" + ((Member)this).index + ")";
        }
        if (this instanceof Argument) {
            return "Context(" + ((Argument)this).index + ")";
        }
        if (this instanceof Local) {
            return "Variable(" + ((Local)this).index + ")";
        }
        if (this instanceof JavaField) {
            return "Java(" + ((JavaField)this).field + ")";
        }
        throw Debug.abort("illegal variable", this);
    }

    //########################################################################
}
