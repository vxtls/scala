/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scala.tools.scalai;

import java.io.PrintWriter;

import scala.runtime.InterpreterSupport.DefinitionPrinter;

import scalac.util.Debug;

public class InterpreterPrinter implements DefinitionPrinter {

    //########################################################################
    // Private Fields

    private final Interpreter interpreter;
    private final PrintWriter writer;

    //########################################################################
    // Public Constructors

    public InterpreterPrinter(Interpreter interpreter, PrintWriter writer) {
        this.interpreter = interpreter;
        this.writer = writer;
    }

    //########################################################################
    // Public Methods

    public void showDefinition(String signature) {
        writer.println(signature);
    }

    public void showValueDefinition(String signature, Object value) {
        EvaluatorResult result = interpreter.toString(value, null);
        if (result instanceof EvaluatorResult.Value) {
            Object string = ((EvaluatorResult.Value)result).value;
            writer.println(signature + " = " + string);
            writer.flush();
            return;
        }
        if (result instanceof EvaluatorResult.Error) {
            EvaluatorException exception = ((EvaluatorResult.Error)result).exception;
            writer.print(signature + " = ");
            writer.print(exception.getScalaErrorMessage(true));
            writer.flush();
            return;
        }
        throw Debug.abort("illegal case", result);
    }

    public void showResult(EvaluatorResult result, boolean interactive) {
        if (result == EvaluatorResult.Void) {
            return;
        }
        if (result instanceof EvaluatorResult.Value) {
            EvaluatorResult.Value valueResult = (EvaluatorResult.Value)result;
            Object value = valueResult.value;
            String type = valueResult.type;
            if (interactive)
                if (value instanceof String)
                    writer.println(value + ": " + type);
                else
                    showResult(interpreter.toString(value, type), interactive);
            writer.flush();
            return;
        }
        if (result instanceof EvaluatorResult.Error) {
            EvaluatorException exception = ((EvaluatorResult.Error)result).exception;
            String name = Thread.currentThread().getName();
            writer.print("Exception in thread \"" + name + "\" ");
            writer.print(exception.getScalaErrorMessage(true));
            writer.flush();
            return;
        }
        throw Debug.abort("illegal case", result);
    }

    //########################################################################
}
