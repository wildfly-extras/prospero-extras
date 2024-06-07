package org.wildfly.prospero.extras.channel.merge;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.extras.ChannelOperations;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.shared.CommandWithHelp;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Produces a channel definition listing repositories from all input channels and using provided manifest.
 */
@CommandLine.Command(name = "merge-repositories", sortOptions = false)
public class ChannelMergeCommand extends CommandWithHelp {

    @CommandLine.Option(names = "--manifest-url", required = true, order = 1)
    URL manifestUrl;
    @CommandLine.Option(names = "--channel", split = ",", required = true, order = 2)
    List<Path> inputChannelPaths;
    @CommandLine.Option(names = "--name", required = false, order = 3)
    String name;
    @CommandLine.Option(names = "--description", required = false, order = 4)
    String description;

    @Override
    public Integer call() throws Exception {
        final Stream<Channel> channels = inputChannelPaths.stream()
                .map(ChannelOperations::read)
                .flatMap(List::stream);

        final Channel channel = merge(channels, manifestUrl, name, description);

        System.out.println(ChannelMapper.toYaml(channel));

        return ReturnCodes.SUCCESS;
    }

    Channel merge(Stream<Channel> channels, URL url, String name, String description) {
        final Channel.Builder channelBuilder = new Channel.Builder()
                .setManifestUrl(url);

        if (name != null) {
            channelBuilder.setName(name);
        }
        if (description != null) {
            channelBuilder.setDescription(description);
        }

        channels
                .map(Channel::getRepositories)
                .flatMap(List::stream)
                .distinct()
                .sorted(Comparator.comparing(Repository::getId))
                .forEach(r -> channelBuilder.addRepository(r.getId(), r.getUrl()));

        return channelBuilder.build();

    }
}
