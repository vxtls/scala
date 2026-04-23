/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class UnknownOptionParser extends OptionParser {

    public UnknownOptionParser(CommandParser command) {
        super(command, "", null);
    }

    public boolean matches(String[] args, int index) {
        return args[index].startsWith("-");
    }

    public int consume(String[] args, int index) {
        command.error("unknown option " + args[index]);
        return index + 1;
    }
}
