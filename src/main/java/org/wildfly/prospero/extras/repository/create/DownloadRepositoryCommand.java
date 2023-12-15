package org.wildfly.prospero.extras.repository.create;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CommandLine.Command(name = "download-repository")
public class DownloadRepositoryCommand implements Callable<Integer> {

    @CommandLine.Option(names={"--out"}, required = true)
    private Path repositoryPath;

    @CommandLine.Option(names={"--channel"}, required = true)
    private Path channelFile;

    @CommandLine.Option(names={"--feature-packs"}, required = true, split = ",")
    private final List<String> featurePacks = new ArrayList<>();

    @CommandLine.Option(names={"--use-zip"}, required = false)
    private boolean useZip = false;

    public static void main(String[] args) throws Exception {
        new DownloadRepositoryCommand().call();
    }

    @Override
    public Integer call() throws Exception {
        final HashSet<Artifact> artifactSet = new HashSet<>();

        // get GAVs of feature-packs;
        // TODO: autodetect from channel

        // for each feature-pack get list of artifacts
        // merge the lists of artifacts
        // apply manifest values for each artifact in the list
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        final RepositorySystem mvnSystem = msm.newRepositorySystem();
        final DefaultRepositorySystemSession mvnSession = msm.newRepositorySystemSession(mvnSystem)
                .setTransferListener(new AbstractTransferListener() {
                    @Override
                    public void transferStarted(TransferEvent event) throws TransferCancelledException {
                        System.out.println("Downloading " + event.getResource().getResourceName());
                    }
                });

        final Channel channel = ChannelMapper.from(channelFile.toUri().toURL());
        final List<RemoteRepository> repositories = channel.getRepositories().stream()
                .map(r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
                .collect(Collectors.toList());
        // TODO: support maven GAV manifest
        final ChannelManifest manifest = ChannelManifestMapper.from(channel.getManifestCoordinate().getUrl());

        for (String featurePackGA : featurePacks) {
            final Optional<Stream> fpStream = manifest.findStreamFor(featurePackGA.split(":")[0], featurePackGA.split(":")[1]);
            if (fpStream.isEmpty()) {
                throw new RuntimeException("The feature pack " + featurePackGA + " cannot be found in the channel.");
            }
            final ArtifactRequest artifactRequest = new ArtifactRequest();
            final DefaultArtifact fpArtifact = new DefaultArtifact(
                    featurePackGA.split(":")[0],
                    featurePackGA.split(":")[1],
                    "artifact-list",
                    "txt",
                    fpStream.get().getVersion()
            );
            artifactRequest.setArtifact(fpArtifact);
            artifactRequest.setRepositories(repositories);
            final ArtifactResult artifactResult = mvnSystem.resolveArtifact(mvnSession, artifactRequest);

            if (!artifactResult.isResolved()) {
                throw new RuntimeException("Unable to resolve artifact " + fpArtifact + " from repositories.");
            }

            if (useZip) {
                final File zipFile = artifactResult.getArtifact().getFile();
                artifactSet.addAll(getArtifactsFromFeaturePackZip(zipFile, manifest));
                artifactSet.add(artifactResult.getArtifact());
            } else {
                final Artifact artifact = artifactResult.getArtifact();
                final File artifactListFile = artifact.getFile();
                FileUtils.readLines(artifactListFile, StandardCharsets.UTF_8).stream()
                        .map(l->l.split(",")[1])
                        .map(CoordUtils::fromLocalPath)
                        .map(a->manifest.findStreamFor(a.getGroupId(), a.getArtifactId()).map(s->a.setVersion(s.getVersion())))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(artifactSet::add);
                artifactSet.add(new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        null,
                        "zip",
                        artifact.getVersion()
                ));
            }
        }

        System.out.println("Downloading");

        // download and deploy the artifact
        final List<ArtifactRequest> requests = artifactSet.stream()
                .map(a -> new ArtifactRequest(a, repositories, null))
                .collect(Collectors.toList());

        final List<ArtifactResult> res = mvnSystem.resolveArtifacts(mvnSession, requests);
        // TODO: check for errors
        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.setRepository(new RemoteRepository
                .Builder("output", "default", repositoryPath.toUri().toURL().toExternalForm())
                .build());
        res.stream().map(ArtifactResult::getArtifact).forEach(deployRequest::addArtifact);
        mvnSystem.deploy(mvnSession, deployRequest);

        // TODO check that all the artifacts in the manifest were downloaded

        return ReturnCodes.SUCCESS;
    }

    private static HashSet<Artifact> getArtifactsFromFeaturePackZip(File zipFile, ChannelManifest manifest) throws IOException {
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

                            final DefaultArtifact mavenArtifact = fromJBossModules(gav);
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

    private static DefaultArtifact fromJBossModules(String str) {
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
