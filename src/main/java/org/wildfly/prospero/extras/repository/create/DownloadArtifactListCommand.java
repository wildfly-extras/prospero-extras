package org.wildfly.prospero.extras.repository.create;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.prospero.extras.ReturnCodes;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@CommandLine.Command(name = "download-artifact-list")
public class DownloadArtifactListCommand implements Callable<Integer> {

    @CommandLine.Option(names={"--out"}, required = true)
    private Path repositoryPath;

    @CommandLine.Option(names={"--artifact-list"}, required = true)
    private Path artifactList;

    @CommandLine.Option(names={"--repositories"}, required = true, split = ",")
    private List<String> repositoriesArg;

    @Override
    public Integer call() throws Exception {
        final List<String> gavLines = Files.readAllLines(artifactList);

        final Set<Artifact> artifacts = gavLines.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(l -> l.split(":"))
                .map(a -> {
                    final String groupId = a[0];
                    final String artifactId = a[1];
                    final String extension = a[2];
                    final String version;
                    final String classifier;
                    if (a.length == 6) {
                        classifier = a[3];
                        version = a[4];
                    } else {
                        classifier = null;
                        version = a[3];
                        if (a.length == 5) {
                            final String scope = a[4];
                            if (!(scope.equals("compile") || scope.equals("provided") || scope.equals("runtime"))) {
                                throw new RuntimeException("Unexpected line: " + String.join(":", a));
                            }
                        }
                    }
                    return new DefaultArtifact(groupId, artifactId, classifier, extension, version);
                })
                .collect(Collectors.toSet());

        // download all artifact
        final AtomicInteger counter = new AtomicInteger(0);
        final List<RemoteRepository> repositories = repositoriesArg.stream()
                .map(a -> {
                    final String id;
                    final String url;
                    if (a.contains("::")) {
                        id = a.split("::")[0].trim();
                        url = a.split("::")[1].trim();
                    } else {
                        id = "repo-" + counter.getAndIncrement();
                        url = a.trim();
                    }
                    return new RemoteRepository.Builder(id, "default", url).build();
                })
                .collect(Collectors.toList());

        new MavenDownloader(repositories)
                .downloadAndDeploy(artifacts, repositoryPath, true, true);

        return ReturnCodes.SUCCESS;
    }
}
