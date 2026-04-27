/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class ScalaProgramArgumentParser extends ArgumentParser {

    public String main;
    public String[] args;

    public ScalaProgramArgumentParser(CommandParser command) {
        super(command);
    }

    public boolean matches(String[] args, int index) {
        return args[index].equals("--");
    }

    public int consume(String[] args, int index) {
        if (index + 1 < args.length) {
            this.main = args[index + 1];
            this.args = new String[args.length - index - 2];
            System.arraycopy(args, index + 2, this.args, 0, this.args.length);
            return args.length;
        } else {
            command.error("option --: missing module name");
            return args.length;
        }
    }
}
