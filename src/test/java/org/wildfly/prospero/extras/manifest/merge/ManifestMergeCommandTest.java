package org.wildfly.prospero.extras.manifest.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;

class ManifestMergeCommandTest {

    protected static final ChannelManifest MANIFEST_ONE = new ChannelManifest(null, null, null, List.of(new Stream("org.test", "test-one", "1.0.0")));
    protected static final ChannelManifest MANIFEST_TWO = new ChannelManifest(null, null, null, List.of(new Stream("org.test", "test-one", "1.1.0")));

    @Test
    public void pickFirstVersion() throws Exception {


        final VersionMergeStrategy strategy = (v1, v2) -> v1;
        final ChannelManifest merged = ManifestMergeCommand.merge(MANIFEST_ONE, MANIFEST_TWO, strategy, null, null);

        assertThat(merged.getStreams())
                .containsOnly(new Stream("org.test", "test-one", "1.0.0"));

    }

    @Test
    public void pickSecondVersion() throws Exception {
        final VersionMergeStrategy strategy = (v1, v2) -> v2;
        final ChannelManifest merged = ManifestMergeCommand.merge(MANIFEST_ONE, MANIFEST_TWO, strategy, null, null);

        assertThat(merged.getStreams())
                .containsOnly(new Stream("org.test", "test-one", "1.1.0"));

    }

    @Test
    public void rejectStream() throws Exception {
        // if the strategy returns null, the stream should be removed
        final VersionMergeStrategy strategy = (v1, v2) -> null;
        final ChannelManifest merged = ManifestMergeCommand.merge(MANIFEST_ONE, MANIFEST_TWO, strategy, null, null);

        assertThat(merged.getStreams())
                .isEmpty();

    }
}