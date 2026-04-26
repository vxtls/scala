/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import java.util.ArrayList;
import java.util.List;

public class ScalaFileArgumentParser extends ArgumentParser {

    public final List list;

    public ScalaFileArgumentParser(CommandParser command) {
        super(command);
        this.list = new ArrayList();
    }

    public boolean matches(String[] args, int index) {
        return args[index].endsWith(".scala");
    }

    public int consume(String[] args, int index) {
        list.add(args[index]);
        return index + 1;
    }

    public String[] toArray() {
        return (String[])list.toArray(new String[list.size()]);
    }
}
