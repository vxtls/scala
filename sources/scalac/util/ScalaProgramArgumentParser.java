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
        this.main = null;
        this.args = new String[0];
    }

    public boolean matches(String[] argv, int index) {
        String arg = argv[index];
        return "--".equals(arg) || isProgramArgument(arg);
    }

    public int consume(String[] argv, int index) {
        if ("--".equals(argv[index])) {
            if (index + 1 >= argv.length) {
                command.error("missing argument after '--'");
                main = null;
                args = new String[0];
                return argv.length;
            }
            main = argv[index + 1];
            int argc = argv.length - index - 2;
            args = new String[argc];
            System.arraycopy(argv, index + 2, args, 0, argc);
            return argv.length;
        }

        int end = index + 1;
        while (end < argv.length && isProgramArgument(argv[end])) end++;
        main = argv[index];
        int argc = end - index - 1;
        args = new String[argc];
        System.arraycopy(argv, index + 1, args, 0, argc);
        return end;
    }

    private boolean isProgramArgument(String arg) {
        return !arg.startsWith("-") && !arg.endsWith(".scala");
    }
}
