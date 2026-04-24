/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class BooleanOptionParser extends OptionParser {

    public boolean value;

    public BooleanOptionParser(CommandParser command,
        String option, String description, boolean value)
    {
        super(command, option, description);
        this.value = value;
    }

    public boolean matches(String[] args, int index) {
        return args[index].equals("-" + option);
    }

    public int consume(String[] args, int index) {
        value = true;
        return index + 1;
    }
}
