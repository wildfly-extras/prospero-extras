package org.wildfly.prospero.extras.manifest.download;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.converters.ManifestConverter;
import org.wildfly.prospero.extras.converters.RepositoriesConverter;
import org.wildfly.prospero.extras.manifest.diff.ManifestsDiffCommand;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.wildfly.installationmanager.MavenOptions.LOCAL_MAVEN_REPO;

@CommandLine.Command(name = "download-diff")
public class DownloadDiffCommand implements Callable<Integer> {

    @CommandLine.Option(names={"--base"}, converter = ManifestConverter.class, required = true)
    private ChannelManifest baseManifest;

    @CommandLine.Option(names={"--update"}, converter = ManifestConverter.class, required = true)
    private ChannelManifest updatedManifest;

    @CommandLine.Option(names={"--repositories"}, converter = RepositoriesConverter.class)
    private List<RemoteRepository> repositories = new ArrayList<>();

    @CommandLine.Option(names={"--out"})
    private Path outputPath;
    @Override
    public Integer call() throws Exception {
        final List<ArtifactChange> artifactChanges = ManifestsDiffCommand.manifestDiff(baseManifest, updatedManifest);

        List<ArtifactRequest> requests = new ArrayList<>();
        for (ArtifactChange c : artifactChanges) {
            if (c.getStatus() == ArtifactChange.Status.REMOVED) {
                continue;
            }

            final String groupId = c.getArtifactName().split(":")[0];
            final String artifactId = c.getArtifactName().split(":")[1];
            final String version = c.getNewVersion();

            // TODO: figure out extension and classifier - feature pack(s)?
            String extension = "jar";
            String classifier = null;

            final DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version, null);
            final ArtifactRequest artifactRequest = new ArtifactRequest(artifact, repositories, null);
            requests.add(artifactRequest);
        }

        System.out.println("Downloading artifacts");
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LOCAL_MAVEN_REPO);
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system, true);

        try {
            final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);
            System.out.println("Downloaded + " + artifactResults.size() + " + artifacts");
            System.out.println();
            System.out.println("Creating local repository");

            final DeployRequest req = new DeployRequest();
            artifactResults.stream()
                    .map(ArtifactResult::getArtifact)
                    .forEach(req::addArtifact);
            req.setRepository(new RemoteRepository.Builder("out", "default", outputPath.toUri().toURL().toExternalForm()).build());
            system.deploy(session, req);

            // TODO: add target manifest
            // TODO: zip the output folder

            System.out.println();
            System.out.println("Done");

        } catch (ArtifactResolutionException e) {
            System.out.println("Unable to find following artifacts:");
            e.getResults().stream()
                    .filter(r -> !r.isResolved())
                    .map(ArtifactResult::getRequest)
                    .forEach(r-> System.out.println(" * " + r.getArtifact()));
            return -1;
        }

        return 0;
    }
}
