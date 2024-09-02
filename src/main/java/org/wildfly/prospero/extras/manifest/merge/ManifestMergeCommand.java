package org.wildfly.prospero.extras.manifest.merge;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.extras.ReturnCodes;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "manifest-merge")
public class ManifestMergeCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0")
    Path manifestOne;

    @CommandLine.Parameters(index = "1")
    Path manifestTwo;

    @CommandLine.Option(names = {"--mode"}, defaultValue = "LATEST")
    VersionMergeStrategy.Strategies mergeStrategy;

    @CommandLine.Option(names = {"--name"}, defaultValue = "merged-manifest")
    String mergedManifestName;

    @CommandLine.Option(names = {"--id"})
    String mergedManifestId;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
    boolean help;

    @Override
    public Integer call() throws Exception {
        final ChannelManifest manifestOne = ChannelManifestMapper.from(this.manifestOne.toUri().toURL());
        final ChannelManifest manifestTwo = ChannelManifestMapper.from(this.manifestTwo.toUri().toURL());

        final ChannelManifest mergedManifest = merge(manifestOne, manifestTwo, mergeStrategy, mergedManifestName, mergedManifestId);

        System.out.println(ChannelManifestMapper.toYaml(mergedManifest));

        return ReturnCodes.SUCCESS;
    }

    public static ChannelManifest merge(ChannelManifest manifestOne, ChannelManifest manifestTwo,
                                        VersionMergeStrategy mergeStrategy,
                                        String mergedManifestName, String mergedManifestId) {
        Objects.requireNonNull(manifestOne);
        Objects.requireNonNull(manifestTwo);
        Objects.requireNonNull(mergeStrategy);

        final Collection<Stream> streamsOne = manifestOne.getStreams();
        final Collection<Stream> streamsTwo = manifestTwo.getStreams();

        final Map<String, Stream> presentKeys = streamsOne.stream().collect(Collectors.toMap(s -> s.getGroupId() + ":" + s.getArtifactId(), s->s));

        Set<Stream> merged = new TreeSet<>(streamsOne);
        for (Stream s : streamsTwo) {
            final String key = s.getGroupId() + ":" + s.getArtifactId();
            final String versionOne;
            final String versionTwo = s.getVersion();
            if (!presentKeys.containsKey(key)) {
                versionOne = null;
            } else {
                versionOne = presentKeys.get(key).getVersion();
            }

            final String version = mergeStrategy.merge(versionOne, versionTwo);
            final Stream streamOne = presentKeys.get(key);
            if (version == null) {
                if (streamOne != null) {
                    merged.remove(streamOne);
                }
            } else if (!version.equals(versionOne)) {
                if (streamOne != null) {
                    merged.remove(presentKeys.get(key));
                }
                merged.add(s);
            }
        }

        return new ChannelManifest(mergedManifestName, mergedManifestId, null, merged);
    }
}
