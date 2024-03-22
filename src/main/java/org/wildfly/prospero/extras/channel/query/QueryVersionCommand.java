package org.wildfly.prospero.extras.channel.query;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.VersionResult;
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

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper OBJECT_MAPPER = (new ObjectMapper(JSON_FACTORY)).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @CommandLine.Option(names={"--channel"}, required = false)
    private Path channelFile;

    @CommandLine.Option(names={"--groupId"})
    private String groupId;

    @CommandLine.Option(names={"--artifactId"})
    private String artifactId;

    @CommandLine.Option(names={"--json"})
    private boolean displayJson;

    @Override
    public Integer call() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        MavenVersionsResolver.Factory factory = new VersionResolverFactory(system, msm.newRepositorySystemSession(system));

        ChannelSession ses = new ChannelSession(List.of(ChannelMapper.from(channelFile.toUri().toURL())), factory);
        final VersionResult version = ses.findLatestMavenArtifactVersion(groupId, artifactId, null, null, null);
        if (version == null) {
            System.err.printf("No version of artifact %s:%s found in the channel.%n", groupId, artifactId);
            return ReturnCodes.ERROR;
        } else {
            if (displayJson) {
                System.out.println(OBJECT_MAPPER.writeValueAsString(new JsonVersionResult(version)));
            } else {
                System.out.println(version.getVersion());
            }
            return ReturnCodes.SUCCESS;
        }
    }

    class JsonVersionResult {

        private final String version;
        private final String channelName;

        JsonVersionResult(VersionResult result) {
            version = result.getVersion();
            channelName = result.getChannelName().orElse(null);
        }

        public String getVersion() {
            return version;
        }

        public String getChannelName() {
            return channelName;
        }
    }
}
