package org.wildfly.prospero.extras.channel;

import org.wildfly.prospero.extras.shared.AbstractParentCommand;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "channel")
public class ChannelCommands extends AbstractParentCommand {

    public ChannelCommands(List<Callable<Integer>> queryVersionCommands) {
        super("channel", queryVersionCommands);
    }
}
