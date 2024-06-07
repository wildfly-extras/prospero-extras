package org.wildfly.prospero.extras;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.prospero.extras.repository.create.MavenDownloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelOperations {

    public static List<Channel> read(Path path) {
        try {
            return ChannelMapper.fromString(Files.readString(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ChannelManifestDownload getChannelManifest(Channel channel) throws ProvisioningException, ArtifactResolutionException, MalformedURLException, VersionRangeResolutionException {
        final List<RemoteRepository> repositories = channel.getRepositories().stream()
            .map(r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
            .collect(Collectors.toList());

        final MavenDownloader downloader = new MavenDownloader(repositories);
        return getChannelManifest(channel, downloader);
    }

    public static ChannelManifestDownload getChannelManifest(Channel channel, MavenDownloader downloader) throws VersionRangeResolutionException, ArtifactResolutionException, MalformedURLException {
        ChannelManifest manifest;

        if (channel.getManifestCoordinate().getUrl() != null) {
            manifest = ChannelManifestMapper.from(channel.getManifestCoordinate().getUrl());
            return new ChannelManifestDownload(manifest, null);
        } else {
            final MavenCoordinate coord = channel.getManifestCoordinate().getMaven();
            final Artifact manifestArtifact = downloader.downloadManifest(ChannelManifestCoordinate.create(null, coord));

            System.out.println("Using manifest: " + manifestArtifact);

            manifest = ChannelManifestMapper.from(manifestArtifact.getFile().toURI().toURL());

            return new ChannelManifestDownload(manifest, manifestArtifact);
        }
    }

    public static final class ChannelManifestDownload {
        public ChannelManifest manifest;
        public Artifact manifestArtifact;

        public ChannelManifestDownload(ChannelManifest manifest, Artifact manifestDownload) {
            this.manifest = manifest;
            this.manifestArtifact = manifestDownload;
        }
    }
}
