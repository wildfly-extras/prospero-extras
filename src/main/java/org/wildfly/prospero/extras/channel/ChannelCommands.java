package org.wildfly.prospero.extras.channel;

import org.wildfly.prospero.extras.shared.AbstractParentCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "channel")
public class ChannelCommands extends AbstractParentCommand {

    public ChannelCommands(CommandLine rootCtx) {
        super("channel", rootCtx);
    }
}
