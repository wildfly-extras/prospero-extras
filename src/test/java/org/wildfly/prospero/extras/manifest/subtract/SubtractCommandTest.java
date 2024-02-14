package org.wildfly.prospero.extras.manifest.subtract;

import org.junit.jupiter.api.Test;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubtractCommandTest {

    @Test
    public void testSubtractTwoEmptySetsProducesEmptySet() throws Exception {
        final ChannelManifest res = callSubtract(new ChannelManifest("", "", "", Collections.emptyList()), getChannelManifest());

        assertThat(res.getStreams())
                .isEmpty();
    }

    @Test
    public void testSubtractEmptySetProducesEmptySet() throws Exception {
        final ChannelManifest c1 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest res = callSubtract(c1, getChannelManifest());

        assertThat(res.getStreams())
                .containsAll(c1.getStreams());
    }

    @Test
    public void testSubtractFromEmptySetProducesOriginalSet() throws Exception {
        final ChannelManifest c1 = getChannelManifest();
        final ChannelManifest c2 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest res = callSubtract(c1, c2);

        assertThat(res.getStreams())
                .isEmpty();
    }

    @Test
    public void testRemoveCommonStream() {
        final ChannelManifest c1 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest c2 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest res = callSubtract(c1, c2);

        assertThat(res.getStreams())
                .isEmpty();
    }

    @Test
    public void testRemoveCommonStreamLivesUnique() {
        final ChannelManifest c1 = getChannelManifest(
                new Stream("foo", "bar", "1.1"),
                new Stream("unique", "one", "1.1")
                );
        final ChannelManifest c2 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest res = callSubtract(c1, c2);

        assertThat(res.getStreams())
                .containsOnly(new Stream("unique", "one", "1.1"));
    }

    @Test
    public void testRemoveCommonStreamIgnoresVersion() {
        final ChannelManifest c1 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest c2 = getChannelManifest(new Stream("foo", "bar", "1.2"));
        final ChannelManifest res = callSubtract(c1, c2);

        assertThat(res.getStreams())
                .isEmpty();
    }

    @Test
    public void testExcludeStreamsByGroupArtifact() {
        final ChannelManifest c1 = getChannelManifest(
                new Stream("foo", "bar", "1.1"),
                new Stream("other", "one", "1.1"));
        final ChannelManifest c2 = getChannelManifest(new Stream("foo", "bar", "1.1"));
        final ChannelManifest res = callSubtract(c1, c2, "foo:bar");

        assertThat(res.getStreams())
                .containsAll(c1.getStreams());
    }

    @Test
    public void testExcludeStreamsByGroup() {
        final ChannelManifest c1 = getChannelManifest(
                new Stream("foo", "bar", "1.1"),
                new Stream("foo", "other", "1.1"));
        final ChannelManifest c2 = getChannelManifest(
                new Stream("foo", "bar", "1.1"),
                new Stream("foo", "other", "1.1")
                );
        final ChannelManifest res = callSubtract(c1, c2, "foo:*");

        assertThat(res.getStreams())
                .containsAll(c1.getStreams());
    }

    @Test
    public void testInvalidExclusionPattern() {
        final ChannelManifest c1 = getChannelManifest();
        final ChannelManifest c2 = getChannelManifest();

        assertThatThrownBy(()->callSubtract(c1, c2, "foo"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(()->callSubtract(c1, c2, "foo:bar:aaa"))
                .isInstanceOf(IllegalArgumentException.class);

        callSubtract(c1, c2, "org.test:bar-aaa122");
    }

    private static ChannelManifest callSubtract(ChannelManifest c1, ChannelManifest c2, String ... exclusions) {
        return ManifestSubtractCommand.subtract(c1, c2, Arrays.asList(exclusions));
    }

    private static ChannelManifest getChannelManifest() {
        return new ChannelManifest("", "", "", Collections.emptyList());
    }

    private static ChannelManifest getChannelManifest(Stream... stream) {
        return new ChannelManifest("", "", "", Arrays.asList(stream));
    }
}