/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

public class ChoiceOptionParser extends OptionParser {

    public final String argument;
    public final String[] choices;
    public String value;

    public ChoiceOptionParser(CommandParser command,
        String option, String description, String argument, String[] choices,
        String value)
    {
        super(command, option, description);
        this.argument = argument;
        this.choices = choices;
        this.value = value;
    }

    public boolean matches(String[] args, int index) {
        return args[index].startsWith("-" + option + ":");
    }

    public int consume(String[] args, int index) {
        String choice = args[index].substring(option.length() + 2);
        boolean found = false;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(choice)) { found = true; break; }
        }
        if (found) {
            value = choice;
        } else if (choice.length() > 0) {
            error("unknown " + argument + " '" + choice + "'");
        } else {
            error("missing " + argument);
        }
        return index + 1;
    }

    public String getHelpSyntax() {
        String syntax = super.getHelpSyntax();
        if (argument != null) syntax = syntax + ":<" + argument + ">";
        return syntax;
    }
}
