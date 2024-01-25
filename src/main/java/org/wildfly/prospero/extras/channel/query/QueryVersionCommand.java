package org.wildfly.prospero.extras.channel.query;

import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.shared.CommandWithHelp;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "query-version")
public class QueryVersionCommand extends CommandWithHelp {

    @CommandLine.Option(names={"--channel"}, required = false)
    private Path channelFile;

    @CommandLine.Option(names={"--groupId"})
    private String groupId;

    @CommandLine.Option(names={"--artifactId"})
    private String artifactId;

    @Override
    public Integer call() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        MavenVersionsResolver.Factory factory = new VersionResolverFactory(system, msm.newRepositorySystemSession(system));

        ChannelSession ses = new ChannelSession(List.of(ChannelMapper.from(channelFile.toUri().toURL())), factory);
        System.out.println(ses.findLatestMavenArtifactVersion(groupId, artifactId, null, null, null));
        return ReturnCodes.SUCCESS;
    }
}
