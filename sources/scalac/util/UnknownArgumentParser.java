/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class UnknownArgumentParser extends ArgumentParser {

    public UnknownArgumentParser(CommandParser command) {
        super(command);
    }

    public boolean matches(String[] args, int index) {
        return true;
    }

    public int consume(String[] args, int index) {
        command.error("don't known what to do with '" + args[index] + "'");
        return index + 1;
    }
}
