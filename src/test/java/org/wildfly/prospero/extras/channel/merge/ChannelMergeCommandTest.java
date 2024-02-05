package org.wildfly.prospero.extras.channel.merge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;

import java.net.URL;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelMergeCommandTest {

    private ChannelMergeCommand channelMergeCommand;


    @BeforeEach
    public void setUp() {
        channelMergeCommand = new ChannelMergeCommand();
    }

    @Test
    public void noRepositories() throws Exception {
        final Channel c1 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-one"))
                .build();
        final Channel c2 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-two"))
                .build();

        final Channel res = channelMergeCommand.merge(Stream.of(c1, c2), new URL("http://new-manifest"), null, null);

        assertThat(res.getRepositories())
                .isEmpty();
    }

    @Test
    public void mergesRepositories() throws Exception {
        final Channel c1 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-one"))
                .addRepository("repo-one", "http://repo-one")
                .build();
        final Channel c2 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-two"))
                .addRepository("repo-two", "http://repo-two")
                .build();

        final Channel res = channelMergeCommand.merge(Stream.of(c1, c2), new URL("http://new-manifest"), null, null);

        assertThat(res.getRepositories())
                .containsExactly(
                        new Repository("repo-one", "http://repo-one"),
                        new Repository("repo-two", "http://repo-two")
                );
    }

    @Test
    public void removesDuplicateRepositories() throws Exception {
        final Channel c1 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-one"))
                .addRepository("repo-one", "http://repo-one")
                .build();
        final Channel c2 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-two"))
                .addRepository("repo-one", "http://repo-one")
                .build();

        final Channel res = channelMergeCommand.merge(Stream.of(c1, c2), new URL("http://new-manifest"), null, null);

        assertThat(res.getRepositories())
                .containsExactly(
                        new Repository("repo-one", "http://repo-one")
                );
    }

    @Test
    public void setNameAndDesctiptionIfAvailable() throws Exception {
        final Channel c1 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-one"))
                .addRepository("repo-one", "http://repo-one")
                .build();
        final Channel c2 = new Channel.Builder()
                .setManifestUrl(new URL("http://manifest-two"))
                .addRepository("repo-two", "http://repo-two")
                .build();

        final Channel res = channelMergeCommand.merge(Stream.of(c1, c2), new URL("http://new-manifest"), "test-name", "test-desc");

        assertThat(res)
                .hasFieldOrPropertyWithValue("name", "test-name")
                .hasFieldOrPropertyWithValue("description", "test-desc");
    }
}