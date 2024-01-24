package org.wildfly.prospero.extras.shared;

import org.wildfly.prospero.extras.ReturnCodes;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

public abstract class AbstractParentCommand implements Callable<Integer> {

    private final String name;
    private final List<Callable<Integer>> subcommands;
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    protected AbstractParentCommand(String name, List<Callable<Integer>> subcommands) {
        this.name = name;
        this.subcommands = subcommands;
    }

    public void addSubCommands(CommandLine rootCmd) {
        CommandLine cmd = rootCmd.getSubcommands().get(name);
        for (Callable<Integer> subcommand : subcommands) {
            cmd.addSubcommand(subcommand);
        }
    }

    @Override
    public Integer call() throws Exception {
        spec.commandLine().usage(System.err);
        return ReturnCodes.INVALID_ARGUMENTS;
    }

}
