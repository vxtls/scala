/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class StringOptionParser extends OptionParser {

    public String value;
    public String argument;

    public StringOptionParser(CommandParser command,
        String option, String description, String argument, String value)
    {
        super(command, option, description);
        this.argument = argument;
        this.value = value;
    }

    public boolean matches(String[] args, int index) {
        return args[index].equals("-" + option);
    }

    public int consume(String[] args, int index) {
        if (index + 1 < args.length) {
            value = args[index + 1];
            return index + 2;
        } else {
            error("missing argument");
            return index + 1;
        }
    }

    public String getHelpSyntax() {
        String syntax = super.getHelpSyntax();
        if (argument != null) syntax = syntax + " <" + argument + ">";
        return syntax;
    }
}
