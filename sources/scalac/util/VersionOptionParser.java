/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class VersionOptionParser extends OptionParser {

    private final String version;

    public VersionOptionParser(CommandParser command,
        String option, String description, String version)
    {
        super(command, option, description);
        this.version = version;
    }

    public boolean matches(String[] args, int index) {
        return args[index].equals("-" + option);
    }

    public int consume(String[] args, int index) {
        System.out.println(version);
        System.exit(0);
        return index + 1;
    }
}
