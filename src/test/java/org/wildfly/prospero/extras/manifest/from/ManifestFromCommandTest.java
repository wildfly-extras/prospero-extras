package org.wildfly.prospero.extras.manifest.from;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestFromCommandTest {

    private List<Path> tempFilesPaths;

    @BeforeEach
    public void setUp() {
        tempFilesPaths = new ArrayList<>();
    }

    @AfterEach
    public void cleanUp() {
        tempFilesPaths.stream().filter(Objects::nonNull).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void shouldGetManifestFromURLInSingleChannel() throws Exception {
        final Channel channel = getChannel(getManifest(new Stream("org.acme", "acme", "1.2.3")));
        final ChannelManifest res = callManifestFrom(List.of(channel));

        assertThat(res.getStreams())
            .containsOnly(new Stream("org.acme", "acme", "1.2.3"));
    }

    @Test
    public void shouldGetManifestFromURLInMultipleChannels() throws Exception {
        final Channel channel1 = getChannel(getManifest(new Stream("org.acme", "acme1", "1.2.3")));
        final Channel channel2 = getChannel(getManifest(new Stream("org.acme", "acme2", "1.2.3")));

        final ChannelManifest res = callManifestFrom(List.of(channel1, channel2));
        assertThat(res.getStreams())
            .containsOnly(new Stream("org.acme", "acme1", "1.2.3"),
                new Stream("org.acme", "acme2", "1.2.3"));
    }

    @Test
    public void shouldNotDuplicateArtifactsInTwoDifferentManifestsFromDifferentChannels() throws Exception {
        final Channel channel1 = getChannel(getManifest(new Stream("org.acme", "acme", "1.2.3")));
        final Channel channel2 = getChannel(getManifest(new Stream("org.acme", "acme", "1.2.3")));

        final ChannelManifest res = callManifestFrom(List.of(channel1, channel2));
        assertThat(res.getStreams())
            .containsOnly(new Stream("org.acme", "acme", "1.2.3"));
    }

    private static ChannelManifest callManifestFrom(List<Channel> channels) {
        return ManifestFromCommand.manifestFrom(channels);
    }

    private Channel getChannel(Path manifestPath) throws IOException {
        return new Channel.Builder()
            .setManifestUrl(manifestPath.toUri().toURL())
            .build();
    }

    private Path getManifest(Stream... streams) throws IOException {
        ChannelManifest channelManifest = new ChannelManifest("", "", "", List.of(streams));

        Path tempFilePath = Files.createTempFile("manifest", "yaml");
        Files.writeString(tempFilePath, ChannelManifestMapper.toYaml(channelManifest));
        tempFilesPaths.add(tempFilePath);

        return tempFilePath;
    }
}