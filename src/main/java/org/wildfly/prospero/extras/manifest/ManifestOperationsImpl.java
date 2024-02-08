package org.wildfly.prospero.extras.manifest;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.manifest.diff.ManifestsDiffCommand;
import org.wildfly.prospero.extras.manifest.merge.ManifestMergeCommand;
import org.wildfly.prospero.extras.manifest.merge.VersionMergeStrategy;

import java.util.List;

class ManifestOperationsImpl implements ManifestOperations {

    @Override
    public ChannelManifest merge(ChannelManifest manifestOne, ChannelManifest manifestTwo,
                                 VersionMergeStrategy.Strategies mergeStrategy,
                                 String mergedManifestName, String mergedManifestId) {
        return ManifestMergeCommand.merge(manifestOne, manifestTwo, mergeStrategy, mergedManifestName, mergedManifestId);
    }

    @Override
    public List<ArtifactChange> diff(ChannelManifest manifestOne, ChannelManifest manifestTwo) {
        return ManifestsDiffCommand.manifestDiff(manifestOne, manifestTwo);
    }
}
