/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Interpreter.java,v 1.63 2002/09/13 01:50:30 paltherr Exp $
// $Id$

package scala.tools.scalai;

import java.util.Map;
import java.util.HashMap;

import scala.tools.util.Position;

import scalac.CompilationUnit;
import scalac.Global;
import scalac.Phase;
import scalac.symtab.Definitions;
import scalac.symtab.Symbol;
import scalac.symtab.Type;
import scalac.symtab.Modifiers;
import scalac.util.Name;

import scala.runtime.InterpreterSupport;
import scala.runtime.InterpreterSupport.EvaluationResult;

public class Interpreter {

    //########################################################################
    // Public Constants

    public static final Name MAIN_N = Name.fromString("main");
    public static final Name ARGS_N = Name.fromString("args");

    //########################################################################
    // Private Fields

    private final Global global;
    private final Compiler compiler;
    private final Evaluator evaluator;

    //########################################################################
    // Public Constructors

    public Interpreter(Global global) {
        this.global = global;
        Map templates = new HashMap();
        this.evaluator = new Evaluator(templates);
        this.compiler = new Compiler(global, templates, evaluator); // !!!
    }

    //########################################################################
    // Public Methods

    public EvaluatorResult invoke(String main, String[]args) {
        Symbol module = getMainModule(main);
        if (module == null) return EvaluatorResult.Void;
        Symbol method = getMainMethod(main, module);
        if (method == null) return EvaluatorResult.Void;
        Variable variable = compiler.getModule(module);
        Function function = compiler.getMethod(method);
        try {
            evaluator.evaluate(variable, function, new Object[] {args});
            return EvaluatorResult.Void;
        } catch (EvaluatorException exception) {
            return EvaluatorResult.Error(exception);
        }
    }

    public EvaluatorResult interpret(String input, boolean interactive) {
        return interpret("<console>", input, interactive);
    }

    public EvaluatorResult interpret(String file, String input,
        boolean interactive)
    {
        if (input.trim().length() == 0) return EvaluatorResult.Void;
        return interpret(
            global.compile(file, input + ";", interactive),
            interactive);
    }

    public EvaluatorResult interpret(String[] files, boolean interactive) {
        if (files.length == 0) return EvaluatorResult.Void;
        return interpret(global.compile(files, interactive), interactive);
    }

    public EvaluatorResult toString(Object object, String type) {
        try {
            return EvaluatorResult.Value(evaluator.toString(object), type);
        } catch (EvaluatorException exception) {
            return EvaluatorResult.Error(exception);
        }
    }

    //########################################################################
    // Private Methods

    private EvaluatorResult interpret(CompilationUnit[] units,
        boolean interactive)
    {
        compiler.compile(units);
        int errors = global.reporter.errors();
        global.reporter.resetCounters();
        if (errors != 0) return EvaluatorResult.Void;
        try {
            if (interactive) {
                Variable console = compiler.getModule(global.console);
                evaluator.evaluate(console);
            }
            EvaluationResult result =
                InterpreterSupport.getAndResetEvaluationResult();
            if (result == null) return EvaluatorResult.Void;
            return EvaluatorResult.Value(result.value, result.type);
        } catch (EvaluatorException exception) {
            return EvaluatorResult.Error(exception);
        }
    }

    //########################################################################
    // Private Methods - Finding main module

    private Symbol getMainModule(String main) {
        String names = main.replace('/', '.') + (main.length() > 0 ? "." : "");
        if (names.length() > 0 && names.charAt(0) == '.') {
            error("illegal module name '" + main + "'");
            return null;
        }
        Symbol module = global.definitions.ROOT_CLASS;
        for (int i = 0, j; (j = names.indexOf('.', i)) >= 0; i = j + 1) {
            Name name = Name.fromString(names.substring(i, j));
            module = getModule(module, name);
            if (module == null) {
                error("could not find module '" + main.substring(0, j) + "'");
                return null;
            }
            if (module == Symbol.NONE) {
                error("term '" + main.substring(0, j) + "' is not a module");
                return null;
            }
        }
        return module;
    }

    private Symbol getModule(Symbol owner, Name name) {
        Symbol symbol = owner.lookup(name);
        if (symbol == Symbol.NONE) return null;
        if (symbol.isModule()) return symbol;
        if (symbol.type() instanceof Type.OverloadedType) {
            Symbol[] alts = ((Type.OverloadedType)symbol.type()).alts;
            for (int k = 0; k < alts.length; k++)
                if (alts[k].isModule()) return alts[k];
        }
        return Symbol.NONE;
    }

    //########################################################################
    // Private Methods - Finding main method

    private Type getMainMethodType(boolean erased) {
        Phase current = global.currentPhase;
        if (!erased) global.currentPhase = global.PHASE.ANALYZER.phase();
        Definitions definitions = global.definitions;
        Type argument = definitions.array_TYPE(definitions.STRING_TYPE());
        Type result = definitions.void_TYPE();
        Symbol formal = Symbol.NONE.newTerm( // !!! should be newVParam
            Position.NOPOS, Modifiers.PARAM, ARGS_N);
        formal.setInfo(argument);
        global.currentPhase = current;
        return Type.MethodType(new Symbol[] {formal}, result);
    }

    private Symbol getMainMethod(String main, Symbol module) {
        Symbol method = getMethod(module, MAIN_N, getMainMethodType(true));
        if (method == null) {
            error("module '" + main + "' has no method '" + MAIN_N + "'");
            return null;
        }
        if (method == Symbol.NONE) {
            error("module '" + main + "' has no method '" + MAIN_N +
                "' with type '" + getMainMethodType(false) + "'");
            return null;
        }
        return method;
    }

    private Symbol getMethod(Symbol module, Name name, Type type) {
        Symbol symbol = module.moduleClass().lookup(name);
        if (symbol == Symbol.NONE) return null;
        if (isMethod(symbol, type)) return symbol;
        if (symbol.type() instanceof Type.OverloadedType) {
            Symbol[] alts = ((Type.OverloadedType)symbol.type()).alts;
            for (int k = 0; k < alts.length; k++)
                if (isMethod(alts[k], type)) return alts[k];
        }
        return Symbol.NONE;
    }

    private boolean isMethod(Symbol symbol, Type type) {
        return symbol.isMethod() && isSameMethodType(symbol.type(), type);
    }

    private boolean isSameMethodType(Type actual, Type expected) {
        return unwrapParameterlessType(actual).isSameAs(unwrapParameterlessType(expected));
    }

    private Type unwrapParameterlessType(Type type) {
        while (type instanceof Type.PolyType
            && ((Type.PolyType)type).tparams.length == 0) {
            type = ((Type.PolyType)type).result;
        }
        return type;
    }

    //########################################################################
    // Private Methods - Signaling errors

    private void error(String message) {
        global.reporter.error(null, message);
    }

    //########################################################################
}
