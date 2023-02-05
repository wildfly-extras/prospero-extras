package org.wildfly.prospero.extras;

import org.wildfly.prospero.extras.bundle.create.CreateBundleCommand;
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

        commandLine.setUsageHelpAutoWidth(true);
        return commandLine;
    }


}
