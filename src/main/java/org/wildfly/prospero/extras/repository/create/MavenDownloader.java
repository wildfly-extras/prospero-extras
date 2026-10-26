package org.wildfly.prospero.extras.repository.create;

import org.apache.http.client.HttpResponseException;
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
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenDownloader {

    private static final Logger LOG = Logger.getLogger(MavenDownloader.class);

    private final RepositorySystem mvnSystem;
    private final DefaultRepositorySystemSession mvnSession;
    private final List<RemoteRepository> repositories;

    public MavenDownloader(List<RemoteRepository> repositories) throws ProvisioningException {
        this(repositories, Collections.emptyMap());
    }

    /**
     * @param repositories remote repositories to resolve from
     * @param configProperties Maven Resolver config properties applied to the repository system session, e.g.
     *                         {@code aether.transport.http.connectTimeout}
     */
    public MavenDownloader(List<RemoteRepository> repositories, Map<String, String> configProperties) throws ProvisioningException {
        this.repositories = repositories;

        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        mvnSystem = msm.newRepositorySystem();
        mvnSession = msm.newRepositorySystemSession(mvnSystem)
                .setTransferListener(new AbstractTransferListener() {
                    @Override
                    public void transferStarted(TransferEvent event) throws TransferCancelledException {
                        System.out.println("Downloading " + event.getResource().getResourceName());
                    }

                    @Override
                    public void transferFailed(TransferEvent event) {
                        final String message = String.format("Transfer of %s from %s failed: %s",
                                event.getResource().getResourceName(),
                                event.getResource().getRepositoryUrl(),
                                describeFailure(event.getException()));
                        if (event.getException() instanceof ArtifactNotFoundException) {
                            // expected when an artifact is searched for in multiple repositories
                            LOG.debug(message);
                        } else {
                            LOG.warn(message, event.getException());
                        }
                    }

                    @Override
                    public void transferCorrupted(TransferEvent event) {
                        LOG.warnf(event.getException(), "Transfer of %s from %s was corrupted: %s",
                                event.getResource().getResourceName(),
                                event.getResource().getRepositoryUrl(),
                                describeFailure(event.getException()));
                    }
                });
        configProperties.forEach(mvnSession::setConfigProperty);
    }

    /**
     * Builds a short description of a transfer failure. If the cause chain contains an HTTP response error, the HTTP
     * status code is included; otherwise the root cause (e.g. connection refused, timeout, unknown host) is reported.
     */
    static String describeFailure(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        Throwable root = failure;
        Throwable current = failure;
        // limit the depth to protect against cyclic cause chains
        for (int i = 0; current != null && i < 20; i++) {
            if (current instanceof HttpResponseException) {
                return "HTTP status " + ((HttpResponseException) current).getStatusCode()
                        + " (" + current.getMessage() + ")";
            }
            root = current;
            current = current.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }

    private static void logResolutionFailures(ArtifactResolutionException e) {
        for (ArtifactResult result : e.getResults()) {
            if (!result.isResolved()) {
                for (Exception cause : result.getExceptions()) {
                    LOG.errorf("Unable to resolve %s: %s", result.getRequest().getArtifact(), describeFailure(cause));
                }
            }
        }
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
        final ArtifactResult artifactResult;
        try {
            artifactResult = mvnSystem.resolveArtifact(mvnSession, artifactRequest);
        } catch (ArtifactResolutionException e) {
            logResolutionFailures(e);
            throw e;
        }
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
        final ArtifactResult artifactResult;
        try {
            artifactResult = mvnSystem.resolveArtifact(mvnSession, artifactRequest);
        } catch (ArtifactResolutionException e) {
            logResolutionFailures(e);
            throw e;
        }
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
                    final String reasons = result.getExceptions().stream()
                            .map(MavenDownloader::describeFailure)
                            .collect(Collectors.joining("; "));
                    if ("sources".equals(result.getRequest().getArtifact().getClassifier())) {
                        System.out.println("WARNING: Unable to resolve sources jar: " + result.getRequest().getArtifact());
                        LOG.warnf("Unable to resolve %s: %s", result.getRequest().getArtifact(), reasons);
                    } else if ("test-sources".equals(result.getRequest().getArtifact().getClassifier())) {
                        System.out.println("WARNING: Unable to resolve sources jar: " + result.getRequest().getArtifact());
                        LOG.warnf("Unable to resolve %s: %s", result.getRequest().getArtifact(), reasons);
                    } else {
                        System.err.println("Unable to resolve: " + result.getRequest().getArtifact());
                        logResolutionFailures(e);
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
