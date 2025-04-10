package org.wildfly.prospero.extras.manifest;

import java.util.Optional;
import java.util.Set;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.version.VersionMatcher;

public class ManifestUtils {

    /**
     * Returns the latest version of the manifest used in the input manfiests
     *
     * @param manifestOne
     * @param manifestTwo
     * @return
     */
    public static Optional<String> getLatestSchemaVersion(ChannelManifest manifestOne, ChannelManifest manifestTwo) {
        final Optional<String> schemaVersion;
        if (manifestOne.getSchemaVersion().equals(manifestTwo.getSchemaVersion()))
            schemaVersion = Optional.of(manifestOne.getSchemaVersion());
        else
            schemaVersion = VersionMatcher.getLatestVersion(Set.of(manifestOne.getSchemaVersion(), manifestTwo.getSchemaVersion()));
        return schemaVersion;
    }
}
