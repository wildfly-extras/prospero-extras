package org.wildfly.prospero.extras;

import org.wildfly.prospero.extras.bundle.create.CreateBundleCommand;
import org.wildfly.prospero.extras.manifest.diff.ManifestsDiffCommand;
import org.wildfly.prospero.extras.manifest.download.DownloadDiffCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = createCommandLine();
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    private static CommandLine createCommandLine() {
        CommandLine commandLine = new CommandLine(new MainCommand());

        commandLine.addSubcommand(new CreateBundleCommand());
        commandLine.addSubcommand(new ManifestsDiffCommand());
        commandLine.addSubcommand(new DownloadDiffCommand());

        commandLine.setUsageHelpAutoWidth(true);
        return commandLine;
    }


}
