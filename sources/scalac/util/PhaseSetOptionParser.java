/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package scalac.util;

import java.util.StringTokenizer;
import scalac.PhaseDescriptor;

public class PhaseSetOptionParser extends OptionParser {

    private final PhaseDescriptor[] phases;
    private final int flag;
    private final PrefixMatcher matcher;

    public PhaseSetOptionParser(CommandParser command,
        String option, String description, PhaseDescriptor[] phases, int flag)
    {
        super(command, option, description);
        this.phases = phases;
        this.flag = flag;
        this.matcher = new PrefixMatcher();
        for (int i = 0; i < phases.length; i++) {
            PhaseDescriptor phase = phases[i];
            matcher.insert(phase.name(), phase, phase.description());
        }
    }

    public boolean matches(String[] args, int index) {
        return args[index].startsWith("-" + option + ":");
    }

    public int consume(String[] args, int index) {
        StringTokenizer tokens = new StringTokenizer(
            args[index].substring(option.length() + 2), ",");
        while (tokens.hasMoreTokens()) consumePhase(tokens.nextToken());
        return index + 1;
    }

    public void consumePhase(String token) {
        if (token.equals("all")) {
            for (int i = 0; i < phases.length; i++)
                phases[i].addFlag(flag, false);
            return;
        }
        PhaseDescriptor phase = lookup(getPhaseName(token));
        if (phase != null) {
            boolean before = getBeforeFlag(token);
            boolean after = getAfterFlag(token) || !before;
            if (before) phase.addFlag(flag, true);
            if (after) phase.addFlag(flag, false);
        }
    }

    public PhaseDescriptor lookup(String name) {
        if (name.length() == 0) {
            error("illegal zero-length phase name");
            return null;
        }
        PrefixMatcher.Entry[] entries = matcher.lookup(name);
        if (entries.length == 1) return (PhaseDescriptor)entries[0].value;
        error(matcher.getErrorMessage(name, entries, "phase name"));
        return null;
    }

    public boolean getBeforeFlag(String token) {
        for (int i = token.length(); 0 < i--; ) {
            switch (token.charAt(i)) {
            case '-': return true;
            case '+': continue;
            default : return false;
            }
        }
        return false;
    }

    public boolean getAfterFlag(String token) {
        for (int i = token.length(); 0 < i--; ) {
            switch (token.charAt(i)) {
            case '-': continue;
            case '+': return true;
            default : return false;
            }
        }
        return false;
    }

    public String getPhaseName(String token) {
        for (int i = token.length(); 0 < i--; ) {
            switch (token.charAt(i)) {
            case '-': continue;
            case '+': continue;
            default : return token.substring(0, i + 1);
            }
        }
        return "";
    }

    public String getHelpSyntax() {
        return super.getHelpSyntax() + ":<phases>";
    }
}
