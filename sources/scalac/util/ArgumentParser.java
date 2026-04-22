/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public abstract class ArgumentParser {

    public final CommandParser command;

    public ArgumentParser(CommandParser command) {
        this.command = command;
    }

    public abstract boolean matches(String[] args, int index);
    public abstract int consume(String[] args, int index);
}
