package org.wildfly.prospero.extras.repository.create;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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
import org.jboss.logging.Logger;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CommandLine.Command(name = "download-repository")
public class DownloadRepositoryCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(DownloadRepositoryCommand.class);
    protected static final int DETECTION_PARALLERLISM = Integer.getInteger("wildfly.prospero.fp.detect_threads", 10);

    @CommandLine.Option(names={"--out"}, required = true)
    private Path repositoryPath;

    @CommandLine.Option(names={"--channel"}, required = true)
    private Path channelFile;

    @CommandLine.Option(names={"--feature-packs"}, split = ",")
    private final List<String> featurePacks = new ArrayList<>();

    @CommandLine.Option(names={"--use-zip"}, required = false)
    private boolean useZip = false;

    public static void main(String[] args) throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        final RepositorySystem mvnSystem = msm.newRepositorySystem();
        final DefaultRepositorySystemSession mvnSession = msm.newRepositorySystemSession(mvnSystem)
                .setTransferListener(new AbstractTransferListener() {
                    @Override
                    public void transferStarted(TransferEvent event) throws TransferCancelledException {
                        System.out.println("Downloading " + event.getResource().getResourceName());
                    }
                });
        new DownloadRepositoryCommand().detectFeaturePacks(mvnSystem, mvnSession, List.of(new Stream("org.jboss.eap", "wildfly-ee-galleon-pack", "8.0.0.GA-redhat-00010")),
                List.of(new RemoteRepository.Builder("brew", "default", "https://download.devel.redhat.com/brewroot/repos/jb-eap-8.0-maven-build/latest/maven/").build()));
    }

    @Override
    public Integer call() throws Exception {
        final HashSet<Artifact> artifactSet = new HashSet<>();

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

        // get GAVs of feature-packs;
        if (featurePacks.isEmpty()) {
            System.out.println("Detecting feature packs");
            featurePacks.addAll(detectFeaturePacks(mvnSystem, mvnSession, manifest.getStreams(), repositories));
            if (featurePacks.isEmpty()) {
                throw new RuntimeException("Unable to find any feature packs in the channel.");
            }
        } else {
            System.out.println("Using defined feature packs");
        }

        System.out.println("Resolving dependencies for:");
        for (String fp : featurePacks) {
            System.out.println("  * " + fp);
        }

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

    private List<String> detectFeaturePacks(RepositorySystem mvnSystem, DefaultRepositorySystemSession mvnSession, Collection<Stream> streams, List<RemoteRepository> repositories) throws IOException {
        final Set<String> featurePacks = new HashSet<>();

        ExecutorService executorService = null;

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            executorService = Executors.newWorkStealingPool(DETECTION_PARALLERLISM);

            List<CompletableFuture<Void>> allTasks = new ArrayList<>();
            for (Stream s : streams) {
                final CompletableFuture<Void> cf = new CompletableFuture<>();
                executorService.submit(() -> {
                    String zipUrl = null;
                    for (RemoteRepository repo : repositories) {
                        final String baseUrl = repo.getUrl();
                        final String url = baseUrl + "/" + s.getGroupId().replaceAll("\\.", "/") + "/" +
                                s.getArtifactId() + "/" + s.getVersion() + "/" + s.getArtifactId() + "-" + s.getVersion() + ".zip";
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Checking " + s + " for feature packs. Trying " + url);
                        }
                        final HttpHead httpHead = new HttpHead(url);
                        try(CloseableHttpResponse res = client.execute(httpHead)) {
                            if (res.getStatusLine().getStatusCode() == 200) {
                                zipUrl = url;
                                break;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (zipUrl != null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Found possible zip: " + zipUrl + ". Downloading to verify.");
                        }
                        // download zip
                        Path tempFile = null;
                        try {
                            tempFile = Files.createTempFile("candidate", "zip", null);
                            IOUtils.copy(new URL(zipUrl), tempFile.toFile());

                            try (ZipFile archive = new ZipFile(tempFile.toFile())) {
                                Enumeration<? extends ZipEntry> entries = archive.entries();
                                while (entries.hasMoreElements()) {
                                    ZipEntry entry = entries.nextElement();
                                    if (entry.getName().equals("feature-pack.xml")) {
                                        LOG.debug("Found feature pack " + s);
                                        featurePacks.add(s.getGroupId() + ":" + s.getArtifactId());
                                    }
                                }
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            if (tempFile != null) {
                                try {
                                    Files.delete(tempFile);
                                } catch (IOException e) {
                                    LOG.error("Unable to remove temporary download: " + tempFile, e);
                                }
                            }
                        }
                    }
                    System.out.println("Checked " + s);
                    cf.complete(null);
                });
                allTasks.add(cf);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Waiting to finish " + allTasks.size() + " tasks");
            }
            CompletableFuture.allOf(allTasks.toArray(new CompletableFuture[]{})).join();
            if (LOG.isDebugEnabled()) {
                LOG.debug("All tasks complete");
            }
        } finally {
            if (executorService != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Shutting down the executor");
                }
                executorService.shutdownNow();
            }
        }
        return new ArrayList<>(featurePacks);
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
