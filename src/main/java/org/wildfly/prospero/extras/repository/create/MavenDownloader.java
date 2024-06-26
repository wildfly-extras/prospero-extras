package org.wildfly.prospero.extras.repository.create;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenDownloader {

    private final RepositorySystem mvnSystem;
    private final DefaultRepositorySystemSession mvnSession;
    private final List<RemoteRepository> repositories;

    public MavenDownloader(List<RemoteRepository> repositories) throws ProvisioningException {
        this.repositories = repositories;

        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        mvnSystem = msm.newRepositorySystem();
        mvnSession = msm.newRepositorySystemSession(mvnSystem)
                .setTransferListener(new AbstractTransferListener() {
                    @Override
                    public void transferStarted(TransferEvent event) throws TransferCancelledException {
                        System.out.println("Downloading " + event.getResource().getResourceName());
                    }
                });
    }

    public Artifact downloadManifest(ChannelManifestCoordinate coord) throws VersionRangeResolutionException, ArtifactResolutionException {
        // resolve version range
        final VersionRangeRequest req = new VersionRangeRequest();
        req.setRepositories(repositories);
        final String version;
        if (coord.getMaven().getVersion() == null) {
            req.setArtifact(new DefaultArtifact(
                    coord.getGroupId(),
                    coord.getArtifactId(),
                    "manifest",
                    "yaml",
                    "[0,)"
            ));
            final VersionRangeResult result = mvnSystem.resolveVersionRange(mvnSession, req);
            final Optional<String> latestVersion = VersionMatcher.getLatestVersion(result.getVersions().stream().map(Version::toString).collect(Collectors.toSet()));

            if (latestVersion.isEmpty()) {
                throw new RuntimeException("Could not find latest version " + coord);
            }
            version = latestVersion.get();
        } else {
            version = coord.getVersion();
        }
        // download the latest version

        final ArtifactRequest artifactRequest = new ArtifactRequest();
        final DefaultArtifact fpArtifact = new DefaultArtifact(
                coord.getGroupId(),
                coord.getArtifactId(),
                "manifest",
                "yaml",
                version
        );
        artifactRequest.setArtifact(fpArtifact);
        artifactRequest.setRepositories(repositories);
        final ArtifactResult artifactResult = mvnSystem.resolveArtifact(mvnSession, artifactRequest);
        if (!artifactResult.isResolved()) {
            throw new RuntimeException("Unable to resolve artifact " + fpArtifact + " from repositories.");
        }
        return artifactResult.getArtifact();
    }

    File download(String groupId, String artifactId, String classifier, String extension, String version) throws ArtifactResolutionException {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        final DefaultArtifact fpArtifact = new DefaultArtifact(
                groupId,
                artifactId,
                classifier,
                extension,
                version
        );
        artifactRequest.setArtifact(fpArtifact);
        artifactRequest.setRepositories(repositories);
        final ArtifactResult artifactResult = mvnSystem.resolveArtifact(mvnSession, artifactRequest);
        if (!artifactResult.isResolved()) {
            throw new RuntimeException("Unable to resolve artifact " + fpArtifact + " from repositories.");
        }
        return artifactResult.getArtifact().getFile();
    }

    void downloadAndDeploy(Set<Artifact> artifactSet, Path outputPath, boolean includeSources, boolean includePoms)
            throws ArtifactResolutionException, MalformedURLException, DeploymentException {
        final List<ArtifactRequest> requests = artifactSet.stream()
                .flatMap(a -> {
                    final ArrayList<Artifact> artifacts = new ArrayList<>();
                    artifacts.add(a);
                    if (includePoms) {
                        Artifact pomArtifact = new DefaultArtifact(
                                a.getGroupId(),
                                a.getArtifactId(),
                                "",
                                "pom",
                                a.getVersion()
                        );
                        artifacts.add(pomArtifact);
                    }
                    if (includeSources && a.getClassifier() != null && a.getExtension().equals("jar")) {
                        Artifact sourcesArtifact = new DefaultArtifact(
                                a.getGroupId(),
                                a.getArtifactId(),
                                "sources",
                                a.getExtension(),
                                a.getVersion()
                        );
                        artifacts.add(sourcesArtifact);
                        Artifact testSourcesArtifact = new DefaultArtifact(
                                a.getGroupId(),
                                a.getArtifactId(),
                                "test-sources",
                                a.getExtension(),
                                a.getVersion()
                        );
                        artifacts.add(testSourcesArtifact);
                    }
                    return artifacts.stream().map(ar->new ArtifactRequest(ar, repositories, null));
                })
                .collect(Collectors.toList());

        List<Artifact> res;
        try {
            res = mvnSystem.resolveArtifacts(mvnSession, requests).stream().map(ArtifactResult::getArtifact).collect(Collectors.toList());
        } catch (ArtifactResolutionException e) {
            ArrayList<Artifact> resolved = new ArrayList<>();
            for (ArtifactResult result : e.getResults()) {
                if (!result.isResolved()) {
                    if ("sources".equals(result.getRequest().getArtifact().getClassifier())) {
                        System.out.println("WARNING: Unable to resolve sources jar: " + result.getRequest().getArtifact());
                    } else if ("test-sources".equals(result.getRequest().getArtifact().getClassifier())) {
                        System.out.println("WARNING: Unable to resolve sources jar: " + result.getRequest().getArtifact());
                    } else {
                        System.err.println("Unable to resolve: " + result.getRequest().getArtifact());
                        throw e;
                    }
                } else {
                    resolved.add(result.getArtifact());
                }
            }
            res = resolved;
        }

        // TODO: check for errors
        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.setRepository(new RemoteRepository
                .Builder("output", "default", outputPath.toUri().toURL().toExternalForm())
                .build());
        res.forEach(deployRequest::addArtifact);
        mvnSystem.deploy(mvnSession, deployRequest);
    }
}
