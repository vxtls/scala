/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scala.tools.scalai;

public abstract class EvaluatorResult {

    //########################################################################
    // Public Cases

    public static final class Void extends EvaluatorResult {
        private Void() {
        }
    }
    public static final Void Void = new Void();

    public static final class Value extends EvaluatorResult {
        public final Object value;
        public final String type;

        private Value(Object value, String type) {
            this.value = value;
            this.type = type;
        }
    }

    public static final class Error extends EvaluatorResult {
        public final EvaluatorException exception;

        private Error(EvaluatorException exception) {
            this.exception = exception;
        }
    }

    //########################################################################
    // Public Factory Methods

    public static Value Value(Object value, String type) {
        return new Value(value, type);
    }

    public static Error Error(EvaluatorException exception) {
        return new Error(exception);
    }

    //########################################################################
}
