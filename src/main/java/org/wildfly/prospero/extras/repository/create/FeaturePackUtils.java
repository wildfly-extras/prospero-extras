package org.wildfly.prospero.extras.repository.create;

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class FeaturePackUtils {

    static boolean isFeaturePack(Path file) throws IOException {
        try (ZipFile archive = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals("feature-pack.xml")) {
                    return true;
                }
            }
        }
        return false;
    }

    static HashSet<Artifact> getArtifactsFromFeaturePackZip(File zipFile, ChannelManifest manifest) throws IOException {
        final HashSet<Artifact> res = new HashSet<>();
        try (ZipFile archive = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();

            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals("resources/wildfly/artifact-versions.properties")) {
                    try (InputStream stream = archive.getInputStream(entry)) {
                        final List<String> artifacts = IOUtils.readLines(stream, StandardCharsets.UTF_8);
                        for (String artifact : artifacts) {
                            final String gav = artifact.split("=")[1];

                            final DefaultArtifact mavenArtifact = fromModulesGav(gav);
                            final Optional<Stream> version = manifest.findStreamFor(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId());
                            //   if the artifact not in the manifest, ignore
                            if (version.isPresent()) {
                                res.add(mavenArtifact.setVersion(version.get().getVersion()));
                            }
                        }
                    }
                    break;
                }
            }
        }
        return res;
    }

    private static DefaultArtifact fromModulesGav(String str) {
        String extension = "jar";
        final String[] parts = str.split(":");
        if(parts.length < 2) {
            throw new IllegalArgumentException("Unexpected artifact coordinates format: " + str);
        }
        final String groupId = parts[0];
        final String artifactId = parts[1];
        String version = null;
        String classifier = null;
        if(parts.length > 2) {
            if(!parts[2].isEmpty()) {
                version = parts[2];
            }
            if(parts.length > 3) {
                if(!parts[3].isEmpty()) {
                    classifier = parts[3];
                }
                if(parts.length > 4) {
                    if(!parts[4].isEmpty()) {
                        extension = parts[4];
                    }
                    if (parts.length > 5) {
                        throw new IllegalArgumentException("Unexpected artifact coordinates format: " + str);
                    }
                }
            }
        }
        return new DefaultArtifact(
                groupId,
                artifactId,
                classifier,
                extension,
                version);
    }
}
