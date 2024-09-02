package org.wildfly.prospero.extras.manifest.merge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionMergeStrategyTest {

    @Test
    public void testLatestVersionMerge() {
        final VersionMergeStrategy latest = new LatestMergeStrategy();
        assertEquals("1.2.4", latest.merge("1.2.3", "1.2.4"));
        assertEquals("1.2.4", latest.merge("1.2.4", "1.2.3"));
        assertEquals("1.2.4", latest.merge("1.2.4", null));
        assertEquals("1.2.4", latest.merge(null, "1.2.4"));
    }

    @Test
    public void testFirstVersionMerge() {
        final VersionMergeStrategy first = new FirstMergeStrategy();
        assertEquals("1.2.3", first.merge("1.2.3", "1.2.4"));
        assertEquals("1.2.4", first.merge("1.2.4", "1.2.3"));
        assertEquals("1.2.4", first.merge("1.2.4", null));
        assertEquals("1.2.4", first.merge(null, "1.2.4"));
    }

    @Test
    public void testLatestExistingVersionMerge() {
        final VersionMergeStrategy strategy = new LatestExistingMergeStrategy();
        assertEquals("1.2.4", strategy.merge("1.2.3", "1.2.4"));
        assertEquals("1.2.4", strategy.merge("1.2.4", "1.2.3"));
        assertEquals("1.2.4", strategy.merge("1.2.4", null));
        assertNull(strategy.merge(null, "1.2.4"));
    }
}