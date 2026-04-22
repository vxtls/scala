/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $OldId: Code.java,v 1.13 2002/09/06 13:04:01 paltherr Exp $
// $Id$

package scala.tools.scalai;

import scalac.symtab.Symbol;
import scalac.util.Debug;

public abstract class Code {

    //########################################################################
    // Public Cases

    public static final class Block extends Code {
        public final Code[] stats;
        public final Code value;

        private Block(Code[] stats, Code value) {
            this.stats = stats;
            this.value = value;
        }
    }

    public static final class Label extends Code {
        public final Symbol symbol;
        public final Variable[] variables;
        public final Code expression;

        private Label(Symbol symbol, Variable[] variables, Code expression) {
            this.symbol = symbol;
            this.variables = variables;
            this.expression = expression;
        }
    }

    public static final class Create extends Code {
        public final ScalaTemplate template;

        private Create(ScalaTemplate template) {
            this.template = template;
        }
    }

    public static final class Invoke extends Code {
        public Code target;
        public final Function function;
        public final Code[] arguments;
        public final int pos;

        private Invoke(Code target, Function function, Code[] arguments, int pos) {
            this.target = target;
            this.function = function;
            this.arguments = arguments;
            this.pos = pos;
        }
    }

    public static final class Load extends Code {
        public final Code target;
        public final Variable variable;

        private Load(Code target, Variable variable) {
            this.target = target;
            this.variable = variable;
        }
    }

    public static final class Store extends Code {
        public final Code target;
        public final Variable variable;
        public final Code expression;

        private Store(Code target, Variable variable, Code expression) {
            this.target = target;
            this.variable = variable;
            this.expression = expression;
        }
    }

    public static final class Synchronized extends Code {
        public final Code object;
        public final Code expression;

        private Synchronized(Code object, Code expression) {
            this.object = object;
            this.expression = expression;
        }
    }

    public static final class If extends Code {
        public final Code cond;
        public final Code thenp;
        public final Code elsep;

        private If(Code cond, Code thenp, Code elsep) {
            this.cond = cond;
            this.thenp = thenp;
            this.elsep = elsep;
        }
    }

    public static final class Or extends Code {
        public final Code lf;
        public final Code rg;

        private Or(Code lf, Code rg) {
            this.lf = lf;
            this.rg = rg;
        }
    }

    public static final class And extends Code {
        public final Code lf;
        public final Code rg;

        private And(Code lf, Code rg) {
            this.lf = lf;
            this.rg = rg;
        }
    }

    public static final class Switch extends Code {
        public final Code test;
        public final int[] tags;
        public final Code[] bodies;
        public final Code otherwise;

        private Switch(Code test, int[] tags, Code[] bodies, Code otherwise) {
            this.test = test;
            this.tags = tags;
            this.bodies = bodies;
            this.otherwise = otherwise;
        }
    }

    public static final class IsScala extends Code {
        public final Code target;
        public final Symbol symbol;

        private IsScala(Code target, Symbol symbol) {
            this.target = target;
            this.symbol = symbol;
        }
    }

    public static final class IsJava extends Code {
        public final Code target;
        public final Class clasz;

        private IsJava(Code target, Class clasz) {
            this.target = target;
            this.clasz = clasz;
        }
    }

    public static final class Literal extends Code {
        public final Object value;

        private Literal(Object value) {
            this.value = value;
        }
    }

    public static final class Self extends Code {
        private Self() {
        }
    }
    public static final Self Self = new Self();

    public static final class Null extends Code {
        private Null() {
        }
    }
    public static final Null Null = new Null();

    //########################################################################
    // Public Factory Methods

    public static Block Block(Code[] stats, Code value) {
        return new Block(stats, value);
    }

    public static Label Label(Symbol symbol, Variable[] variables, Code expression) {
        return new Label(symbol, variables, expression);
    }

    public static Create Create(ScalaTemplate template) {
        return new Create(template);
    }

    public static Invoke Invoke(Code target, Function function, Code[] arguments, int pos) {
        return new Invoke(target, function, arguments, pos);
    }

    public static Load Load(Code target, Variable variable) {
        return new Load(target, variable);
    }

    public static Store Store(Code target, Variable variable, Code expression) {
        return new Store(target, variable, expression);
    }

    public static Synchronized Synchronized(Code object, Code expression) {
        return new Synchronized(object, expression);
    }

    public static If If(Code cond, Code thenp, Code elsep) {
        return new If(cond, thenp, elsep);
    }

    public static Or Or(Code lf, Code rg) {
        return new Or(lf, rg);
    }

    public static And And(Code lf, Code rg) {
        return new And(lf, rg);
    }

    public static Switch Switch(Code test, int[] tags, Code[] bodies, Code otherwise) {
        return new Switch(test, tags, bodies, otherwise);
    }

    public static IsScala IsScala(Code target, Symbol symbol) {
        return new IsScala(target, symbol);
    }

    public static IsJava IsJava(Code target, Class clasz) {
        return new IsJava(target, clasz);
    }

    public static Literal Literal(Object value) {
        return new Literal(value);
    }

    //########################################################################
    // Public Methods

    public String toString() {
        if (this instanceof Block) {
            Block block = (Block)this;
            StringBuffer buffer = new StringBuffer();
            buffer.append("Block([").append('\n');
            for (int i = 0; i < block.stats.length; i++) {
                if (i > 0) buffer.append(",\n");
                buffer.append(block.stats[i]);
            }
            buffer.append("], ").append(block.value).append(")");
            return buffer.toString();
        }
        if (this instanceof Label) {
            Label label = (Label)this;
            StringBuffer buffer = new StringBuffer();
            buffer.append("Label(").append(label.symbol).append(",[");
            for (int i = 0; i < label.variables.length; i++) {
                if (i > 0) buffer.append(",\n");
                buffer.append(label.variables[i]);
            }
            buffer.append("],").append(label.expression).append(")");
            return buffer.toString();
        }
        if (this instanceof Create) {
            return "Create(" + ((Create)this).template + ")";
        }
        if (this instanceof Invoke) {
            Invoke invoke = (Invoke)this;
            StringBuffer buffer = new StringBuffer();
            buffer.append("Invoke(").append(invoke.target).append(",").append(invoke.function).append(",").append("[\n");
            for (int i = 0; i < invoke.arguments.length; i++) {
                if (i > 0) buffer.append(",\n");
                buffer.append(invoke.arguments[i]);
            }
            buffer.append("])");
            return buffer.toString();
        }
        if (this instanceof Load) {
            Load load = (Load)this;
            return "Load(" + load.target + "," + load.variable + ")";
        }
        if (this instanceof Store) {
            Store store = (Store)this;
            return "Store(" + store.target + "," + store.variable + "," + store.expression + ")";
        }
        if (this instanceof If) {
            If branch = (If)this;
            return "If(" + branch.cond + "," + branch.thenp + "," + branch.elsep + ")";
        }
        if (this instanceof Or) {
            Or or = (Or)this;
            return "Or(" + or.lf + "," + or.rg + ")";
        }
        if (this instanceof And) {
            And and = (And)this;
            return "And(" + and.lf + "," + and.rg + ")";
        }
        if (this instanceof Switch) {
            Switch switch_ = (Switch)this;
            StringBuffer buffer = new StringBuffer();
            buffer.append("Switch(").append(switch_.test).append(",\n");
            for (int i = 0; i < switch_.bodies.length; i++) {
                buffer.append(switch_.tags[i]).append(" => ").append(switch_.bodies[i]);
                buffer.append(",\n");
            }
            buffer.append("_  => ").append(switch_.otherwise);
            buffer.append(")");
            return buffer.toString();
        }
        if (this instanceof IsScala) {
            IsScala isScala = (IsScala)this;
            return "IsScala(" + isScala.target + "," + isScala.symbol + ")";
        }
        if (this instanceof IsJava) {
            IsJava isJava = (IsJava)this;
            return "IsJava(" + isJava.target + "," + isJava.clasz + ")";
        }
        if (this instanceof Literal) {
            return "Literal(" + ((Literal)this).value + ")";
        }
        if (this == Self) {
            return "Self";
        }
        if (this == Null) {
            return "Null";
        }
        throw Debug.abort("illegal code", this);
    }

    //########################################################################
}
