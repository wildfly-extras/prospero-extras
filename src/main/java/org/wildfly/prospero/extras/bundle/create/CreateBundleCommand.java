package org.wildfly.prospero.extras.bundle.create;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.logging.Logger;
import org.wildfly.prospero.promotion.ArtifactBundle;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.wildfly.installationmanager.MavenOptions.LOCAL_MAVEN_REPO;

@CommandLine.Command(name = "create-bundle")
public class CreateBundleCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(CreateBundleCommand.class);

    @CommandLine.Option(names={"--out"}, required = true)
    private Path bundlePath;

    @CommandLine.Option(names={"--artifact", "--artifacts"}, required = true)
    private List<String> artifacts = new ArrayList<>();

    @CommandLine.Option(names={"--repository", "--repositories"})
    private List<String> repositories = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        LOG.info("Building artifact requests");
        List<ArtifactRequest> requests = new ArrayList<>();
        final List<RemoteRepository> mvnRepositories = parseRepositories();

        for (String artifactSpec : artifacts) {
            final String[] gav = artifactSpec.split(":");
            if (gav.length < 3) {
                LOG.error("The artifact GAV is expected to contain at least 3 elements");
                return -2;
            }

            String version = gav[2];
            String classifier = null;
            String extension = "jar";

            if (gav.length == 4) {
                version = gav[3];
                classifier = gav[2];
            }
            if (gav.length == 5) {
                version = gav[4];
                classifier = gav[2];
                extension = gav[3];
            }
            final DefaultArtifact artifact = new DefaultArtifact(gav[0], gav[1], classifier, extension, version, null);
            final ArtifactRequest artifactRequest = new ArtifactRequest(artifact, mvnRepositories, null);
            LOG.infof("{groupId: %s, artifactId: %s, classifier: %s, extension %s, version: %s}",
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                    artifact.getExtension(), artifact.getVersion());
            requests.add(artifactRequest);
        }

        LOG.info("Downloading artifacts");
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LOCAL_MAVEN_REPO);
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system, true);
        try {
            final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);

            final List<Artifact> downloadedArtifacts = artifactResults.stream().map(ArtifactResult::getArtifact).collect(Collectors.toList());

            LOG.info("Creating bundle");
            ArtifactBundle.createCustomizationArchive(downloadedArtifacts, bundlePath.toFile());
            return 0;
        } catch (ArtifactResolutionException e) {
            LOG.warn("Unable to find following artifacts:");
            e.getResults().stream()
                    .filter(r -> !r.isResolved())
                    .map(ArtifactResult::getRequest)
                    .forEach(r-> LOG.warn(" * " + r.getArtifact()));
            return -1;
        }
    }

    private List<RemoteRepository> parseRepositories() throws MalformedURLException {
        List<RemoteRepository> mavenRepos = new ArrayList<>();
        int i = 0;
        for (String spec : repositories) {
            if (spec.contains("::")) {
                final String[] splitSpec = spec.split("::");
                final String id = splitSpec[0];
                final URL url = new URL(splitSpec[1]);
                mavenRepos.add(new RemoteRepository.Builder(id, "default", url.toExternalForm()).build());
            } else {
                final URL url = new URL(spec);
                final String id = "repo-" + i++;
                mavenRepos.add(new RemoteRepository.Builder(id, "default", url.toExternalForm()).build());
            }
        }
        return mavenRepos;
    }
}
