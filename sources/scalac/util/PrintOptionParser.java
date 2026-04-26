/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import scalac.PhaseDescriptor;

public class PrintOptionParser extends PhaseSetOptionParser {

    public boolean tokens;

    public PrintOptionParser(CommandParser command,
        String option, String description, PhaseDescriptor[] phases, int flag)
    {
        super(command, option, description, phases, flag);
        this.tokens = false;
    }

    public void consumePhase(String token) {
        if ("tokens".equals(token))
            tokens = true;
        else
            super.consumePhase(token);
    }
}
