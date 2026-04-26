/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import scala.tools.util.Position;
import scala.tools.util.Reporter;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class CommandParser {

    private final String product;
    private final String version;
    private final String syntax;
    private final Reporter reporter;
    private final List/*<ArgumentParser>*/ parsers;

    public CommandParser(String product, String version, String syntax,
        Reporter reporter)
    {
        this.product = product;
        this.version = version;
        this.syntax = syntax;
        this.reporter = reporter;
        this.parsers = new ArrayList();
    }

    public String product() {
        return product;
    }

    public String version() {
        return version;
    }

    public String syntax() {
        return syntax;
    }

    public Reporter reporter() {
        return reporter;
    }

    public boolean add(ArgumentParser parser) {
        return parsers.add(parser);
    }

    public void add(int index, ArgumentParser parser) {
        parsers.add(index, parser);
    }

    public boolean remove(ArgumentParser parser) {
        return parsers.remove(parser);
    }

    public List parsers() {
        return parsers;
    }

    public boolean parse(String[] args) {
        int errors = reporter.errors();
        for (int i = 0; i < args.length; ) {
            for (int j = 0; j < parsers.size(); j++) {
                ArgumentParser parser = (ArgumentParser)parsers.get(j);
                if (parser.matches(args, i)) {
                    i = parser.consume(args, i);
                    break;
                }
            }
        }
        return reporter.errors() == errors;
    }

    public String getHelpMessage() {
        Format format = new MessageFormat("  {0}\t  {1}");
        List options = new ArrayList(parsers.size());
        for (int i = 0; i < parsers.size(); i++) {
            if (!(parsers.get(i) instanceof OptionParser)) continue;
            OptionParser parser = (OptionParser)parsers.get(i);
            String option = parser.getHelpMessage(format);
            if (option != null) options.add(option);
        }
        StringBuffer buffer = new StringBuffer();
        buffer.append("usage: ").append(product());
        if (options.size() > 0) buffer.append(" <options>");
        if (syntax != null) buffer.append(' ').append(syntax);
        buffer.append(Strings.EOL);
        if (options.size() > 0) {
            buffer.append("where possible options include:");
            buffer.append(Strings.EOL);
            buffer.append(Strings.format(options));
        }
        return buffer.toString();
    }

    public void error(String message) {
        reporter.error(new Position(product), message);
    }

    public void warning(String message) {
        reporter.warning(new Position(product), message);
    }
}
