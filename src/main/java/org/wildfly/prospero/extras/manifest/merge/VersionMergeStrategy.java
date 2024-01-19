package org.wildfly.prospero.extras.manifest.merge;

import org.wildfly.channel.version.VersionMatcher;

interface VersionMergeStrategy {
    enum Strategies implements VersionMergeStrategy {
        LATEST(new LatestMergeStrategy()),
        FIRST(new FirstMergeStrategy());

        private final VersionMergeStrategy mergeStrategy;

        Strategies(VersionMergeStrategy mergeStrategy) {
            this.mergeStrategy = mergeStrategy;
        }

        public String merge(String v1, String v2) {
            return mergeStrategy.merge(v1, v2);
        }
    }
    String merge(String v1, String v2);
}

class FirstMergeStrategy implements VersionMergeStrategy {

    @Override
    public String merge(String v1, String v2) {
        return v1;
    }
}

class LatestMergeStrategy implements VersionMergeStrategy {

    @Override
    public String merge(String v1, String v2) {
        if (VersionMatcher.COMPARATOR.compare(v2, v1) > 0) {
            return v2;
        } else {
            return v1;
        }
    }
}
