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

import org.wildfly.prospero.extras.bundle.create.CreateBundleCommand;
import org.wildfly.prospero.extras.channel.ChannelCommands;
import org.wildfly.prospero.extras.channel.merge.ChannelMergeCommand;
import org.wildfly.prospero.extras.channel.query.QueryVersionCommand;
import org.wildfly.prospero.extras.manifest.diff.ManifestsDiffCommand;
import org.wildfly.prospero.extras.manifest.download.DownloadDiffCommand;
import org.wildfly.prospero.extras.manifest.merge.ManifestMergeCommand;
import org.wildfly.prospero.extras.repoository.RepositoryCommands;
import org.wildfly.prospero.extras.repository.create.DownloadArtifactListCommand;
import org.wildfly.prospero.extras.repository.create.DownloadRepositoryCommand;
import org.wildfly.prospero.extras.repository.create.RepositoryDownloadCommands;
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
        commandLine.addSubcommand(new ManifestMergeCommand());

        commandLine.addSubcommand(new DownloadRepositoryCommand());
        commandLine.addSubcommand(new DownloadArtifactListCommand());

        final ChannelCommands channelSubcommand = new ChannelCommands(commandLine);
        commandLine.addSubcommand(channelSubcommand);
        channelSubcommand.addSubCommand(new QueryVersionCommand());
        channelSubcommand.addSubCommand(new ChannelMergeCommand());

        final RepositoryCommands repositoryCommands = new RepositoryCommands(commandLine);
        commandLine.addSubcommand(repositoryCommands);
        final RepositoryDownloadCommands repoDownloadCommands = new RepositoryDownloadCommands(repositoryCommands.getCtx());
        repositoryCommands.addSubCommand(repoDownloadCommands);
        repoDownloadCommands.addSubCommand(new DownloadArtifactListCommand());
        repoDownloadCommands.addSubCommand(new DownloadRepositoryCommand());

        commandLine.setUsageHelpAutoWidth(true);
        return commandLine;
    }

}
