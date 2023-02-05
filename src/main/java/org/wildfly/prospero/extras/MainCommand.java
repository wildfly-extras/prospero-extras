package org.wildfly.prospero.extras;

import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "tools")
public class MainCommand implements Callable<Integer> {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @SuppressWarnings("unused")
    @CommandLine.Option(names = {CliConstants.H, CliConstants.HELP}, usageHelp = true)
    boolean help;

    @Override
    public Integer call() throws IOException {
        // print main command usage
        spec.commandLine().usage(System.out);
        return ReturnCodes.SUCCESS;
    }
}
