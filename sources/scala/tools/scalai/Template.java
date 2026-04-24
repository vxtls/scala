/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Template.java,v 1.2 2002/06/28 17:23:59 paltherr Exp $
// $Id$

package scala.tools.scalai;

import scalac.util.Debug;

public abstract class Template {

    //########################################################################
    // Public Cases

    public static final class Global extends Template {
        public final ScalaTemplate template;

        private Global(ScalaTemplate template) {
            this.template = template;
        }
    }

    public static final class JavaClass extends Template {
        public final Class clasz;

        private JavaClass(Class clasz) {
            this.clasz = clasz;
        }
    }

    //########################################################################
    // Public Factory Methods

    public static Global Global(ScalaTemplate template) {
        return new Global(template);
    }

    public static JavaClass JavaClass(Class clasz) {
        return new JavaClass(clasz);
    }

    //########################################################################
    // Public Methods

    public String toString() {
        if (this instanceof Global) {
            return "Global(" + ((Global)this).template + ")";
        }
        if (this instanceof JavaClass) {
            return "JavaClass(" + ((JavaClass)this).clasz + ")";
        }
        throw Debug.abort("unknown case", this);
    }

    //########################################################################
}
