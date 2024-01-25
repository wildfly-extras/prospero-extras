package org.wildfly.prospero.extras.repoository;

import org.wildfly.prospero.extras.shared.AbstractParentCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "repository")
public class RepositoryCommands extends AbstractParentCommand {

    public RepositoryCommands(CommandLine ctx) {
        super("repository", ctx);
    }
}
