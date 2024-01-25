package org.wildfly.prospero.extras.shared;

import org.wildfly.prospero.extras.ReturnCodes;
import picocli.CommandLine;

public abstract class AbstractParentCommand implements Command {

    private final String name;
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;
    private CommandLine rootCtx;

    public AbstractParentCommand(String name, CommandLine rootCtx) {
        this.rootCtx = rootCtx;
        this.name = name;
    }

    @Override
    public Integer call() throws Exception {
        spec.commandLine().usage(System.err);
        return ReturnCodes.INVALID_ARGUMENTS;
    }

    public void addSubCommand(Command command) {
        getCtx().addSubcommand(command);
    }

    public CommandLine getCtx() {
        return rootCtx.getSubcommands().get(name);
    }
}
