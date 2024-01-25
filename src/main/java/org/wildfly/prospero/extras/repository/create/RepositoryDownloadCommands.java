package org.wildfly.prospero.extras.repository.create;

import org.wildfly.prospero.extras.shared.AbstractParentCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "download")
public class RepositoryDownloadCommands extends AbstractParentCommand {

    public RepositoryDownloadCommands(CommandLine rootCtx) {
        super("download", rootCtx);
    }
}
