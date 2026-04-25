/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class HelpOptionParser extends OptionParser {

    public HelpOptionParser(CommandParser command,
        String option, String description)
    {
        super(command, option, description);
    }

    public boolean matches(String[] args, int index) {
        return args[index].equals("-?") ||
            args[index].equals("-" + option) ||
            args[index].equals("--" + option);
    }

    public int consume(String[] args, int index) {
        System.out.println(command.getHelpMessage());
        System.exit(0);
        return index + 1;
    }

    public String getHelpSyntax() {
        return "-? " + super.getHelpSyntax();
    }
}
