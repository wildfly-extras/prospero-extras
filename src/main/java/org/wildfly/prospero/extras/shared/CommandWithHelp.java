package org.wildfly.prospero.extras.shared;

import picocli.CommandLine;

public abstract class CommandWithHelp implements Command {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, order = Integer.MAX_VALUE)
    boolean help;
}
