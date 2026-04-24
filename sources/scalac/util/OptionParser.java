/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import scala.tools.util.Position;

import java.text.Format;
import java.util.ArrayList;
import java.util.List;

public abstract class OptionParser extends ArgumentParser {

    public final String option;
    public final String description;

    public OptionParser(CommandParser command, String option,
        String description)
    {
        super(command);
        this.option = option;
        this.description = description;
    }

    public String getHelpSyntax() {
        return "-" + option;
    }

    public String getHelpDescription() {
        return description;
    }

    public void getHelpMessageArgs(List args) {
        args.add(getHelpSyntax());
        args.add(getHelpDescription());
    }

    public String getHelpMessage(Format format) {
        if (description == null) return null;
        List args = new ArrayList();
        getHelpMessageArgs(args);
        return format.format(args.toArray());
    }

    public void error(String message) {
        command.error("option -" + option + ": " + message);
    }

    public void warning(String message) {
        command.warning("option -" + option + ": " + message);
    }
}
