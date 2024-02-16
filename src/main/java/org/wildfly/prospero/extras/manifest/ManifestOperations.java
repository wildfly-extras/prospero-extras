package org.wildfly.prospero.extras.manifest;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.manifest.merge.VersionMergeStrategy;

import java.util.List;

public interface ManifestOperations {

    /**
     * Merges streams from two manifests.
     *
     * Creates a manifest containing streams from both input manifests. If the same stream is available in both input
     * manifests, the conflict is resolved using a merge strategy.
     * <ul>
     *     <li>The LATEST merge strategy compares the versions and picks the latest stream.</li>
     *     <li>The FIRST merge strategy chooses the stream from {@code manifestOne}.</li>
     * </ul>
     * @param manifestOne - first manifest to merge
     * @param manifestTwo - second manifest to merge
     * @param mergeStrategy - merging strategy used if the stream is found in both manifests
     * @param mergedManifestName - optional name of the generated manifest
     * @param mergedManifestId - optional id of the generated manifest
     * @return - merged manifest
     */
    ChannelManifest merge(ChannelManifest manifestOne, ChannelManifest manifestTwo,
                          VersionMergeStrategy.Strategies mergeStrategy,
                          String mergedManifestName, String mergedManifestId);

    /**
     * Subtracts streams of two manifests.
     *
     * Returns a manifest containing only streams from the first manifest that are The versions of artifacts in streams are ignored.
     *
     * The excluded streams are always included in the output manifest even if they are present in the second manifest.
     *
     * @param manifestOne - Initial manifest that the streams will be removed from.
     * @param manifestTwo - Manifest containing streams to be removed.
     * @param exclusions - list of excluded streams. To include all streams matching a group a wildcard syntax.
     * @return
     */
    ChannelManifest subtract(ChannelManifest manifestOne, ChannelManifest manifestTwo,
                             List<String> exclusions);

    /**
     * Performs a diff of {@code manifestOne} and {@code manifestTwo}.
     *
     * @param manifestOne - first manifest to diff
     * @param manifestTwo - second manifest to diff
     * @return - list of changed streams representes as {@link ArtifactChange}s
     */
    List<ArtifactChange> diff(ChannelManifest manifestOne, ChannelManifest manifestTwo);
}
