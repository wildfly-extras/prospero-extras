package org.wildfly.prospero.extras.manifest.from;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.prospero.extras.ChannelOperations;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.manifest.merge.ManifestMergeCommand;
import org.wildfly.prospero.extras.manifest.merge.VersionMergeStrategy;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "manifest-from")
public class ManifestFromCommand implements Callable<Integer> {
    @CommandLine.Option(names = "--channel", split = ",", required = true)
    List<Path> inputChannelPaths;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
    boolean help;

    @Override
    public Integer call() throws Exception {
        List<Channel> channelList = inputChannelPaths.stream()
            .map(ChannelOperations::read)
            .flatMap(List::stream)
            .collect(Collectors.toList());

        final ChannelManifest channelManifest = manifestFrom(channelList);

        System.out.println(ChannelManifestMapper.toYaml(channelManifest));

        return ReturnCodes.SUCCESS;
    }

    public static ChannelManifest manifestFrom(List<Channel> channelPaths) {
        return channelPaths.stream()
            .map(channel -> {
                try {
                    ChannelOperations.ChannelManifestDownload channelManifestDownload = ChannelOperations.getChannelManifest(channel);
                    return channelManifestDownload.manifest;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .reduce((m1, m2) -> ManifestMergeCommand.merge(m1, m2, VersionMergeStrategy.Strategies.LATEST, null, null))
            .orElseThrow(() -> new RuntimeException("Couldn't extract manifest from the provided channels"));
    }
}
