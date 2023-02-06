/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.extras.manifest.download;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.converters.ManifestConverter;
import org.wildfly.prospero.extras.converters.RepositoriesConverter;
import org.wildfly.prospero.extras.manifest.diff.ManifestsDiffCommand;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.wildfly.installationmanager.MavenOptions.LOCAL_MAVEN_REPO;

// TODO: handle use case with only baseManifest and update-gav
@CommandLine.Command(name = "download-diff")
public class DownloadDiffCommand implements Callable<Integer> {

    @CommandLine.Option(names={"--base"}, converter = ManifestConverter.class, required = true)
    private ChannelManifest baseManifest;

    @CommandLine.Option(names={"--update"}, converter = ManifestConverter.class, required = true)
    private ChannelManifest updatedManifest;

    @CommandLine.Option(names={"--update-gav"}, required = false)
    private String updatedManifestGav;

    @CommandLine.Option(names={"--repositories"}, converter = RepositoriesConverter.class)
    private List<RemoteRepository> repositories = new ArrayList<>();


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
            if (artifactId.equals("hal-console") || artifactId.equals("wildfly-ee-galleon-pack")) {
                continue;
            }
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

        Path tempFile = null;
        try {
            final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);
            System.out.println("Downloaded + " + artifactResults.size() + " + artifacts");
            System.out.println();
            System.out.println("Creating local repository " + outputPath);

            final DeployRequest req = new DeployRequest();
            artifactResults.stream()
                    .map(ArtifactResult::getArtifact)
                    .forEach(req::addArtifact);

            // add target manifest
            if (updatedManifestGav != null) {
                final String[] splitGav = updatedManifestGav.split(":");
                final String groupId = splitGav[0];
                final String artifactId = splitGav[1];
                final String version = splitGav[2];

                tempFile = Files.createTempFile("manifest", "yaml");
                Files.writeString(tempFile, ChannelManifestMapper.toYaml(updatedManifest));
                req.addArtifact(new DefaultArtifact(groupId, artifactId, "manifest", "yaml", version, null, tempFile.toFile()));
            }

            final Path tempDirectory = Files.createTempDirectory("prospero-repo");
            try {
                req.setRepository(new RemoteRepository.Builder("out", "default", tempDirectory.toUri().toURL().toExternalForm()).build());
                system.deploy(session, req);

                // zip the output folder
                createArchive(tempDirectory, outputPath);
            } finally {
                FileUtils.deleteQuietly(tempDirectory.toFile());
            }

            System.out.println();
            System.out.println("Done");

        } catch (ArtifactResolutionException e) {
            System.out.println("Unable to find following artifacts:");
            e.getResults().stream()
                    .filter(r -> !r.isResolved())
                    .map(ArtifactResult::getRequest)
                    .forEach(r-> System.out.println(" * " + r.getArtifact()));
            return -1;
        } finally {
            if (tempFile != null) {
                Files.delete(tempFile);
            }
        }

        return 0;
    }

    @CommandLine.Option(names={"--out"})
    private Path outputPath;

    public static Path createArchive(Path artifacts, Path archive) throws IOException {
        Objects.requireNonNull(artifacts);
        Objects.requireNonNull(archive);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive.toFile()))) {
            Files.walkFileTree(artifacts, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    final String entry = artifacts.relativize(dir).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entry + '/'));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final String entry = artifacts.relativize(file).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entry));
                    try(FileInputStream fis = new FileInputStream(file.toFile())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    return null;
    }
}
