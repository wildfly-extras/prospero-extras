/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.extras;

import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "tools", resourceBundle = "UsageMessages")
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
