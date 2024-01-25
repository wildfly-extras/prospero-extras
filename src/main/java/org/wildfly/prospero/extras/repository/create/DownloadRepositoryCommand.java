package org.wildfly.prospero.extras.repository.create;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.shared.CommandWithHelp;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;

@CommandLine.Command(name = "from-channel")
public class DownloadRepositoryCommand extends CommandWithHelp {

    private static final Logger LOG = Logger.getLogger(DownloadRepositoryCommand.class);

    @CommandLine.Option(names={"--out"}, required = true)
    private Path repositoryPath;

    @CommandLine.Option(names={"--channel"}, required = true)
    private Path channelFile;

    @CommandLine.Option(names={"--feature-packs"}, split = ",")
    private final List<String> featurePacks = new ArrayList<>();

    @CommandLine.Option(names={"--fp-mapper"})
    private FpMapperValues fpMapper = FpMapperValues.ZIP;

    @CommandLine.Option(names={"--include-sources"})
    private boolean includeSources = false;

    @CommandLine.Option(names={"--include-poms"})
    private boolean includePoms = false;

    @CommandLine.Option(names={"--artifact-list"})
    private boolean artifactList = false;
    private final ChannelFeaturePackResolver channelFeaturePackResolver = new ChannelFeaturePackResolver();

    enum FpMapperValues { ZIP, OFFLINER }

    public DownloadRepositoryCommand() {

    }

    @Override
    public Integer call() throws Exception {

        // for each feature-pack get list of artifacts
        // merge the lists of artifacts
        // apply manifest values for each artifact in the list

        final Channel channel = ChannelMapper.from(channelFile.toUri().toURL());
        final List<RemoteRepository> repositories = channel.getRepositories().stream()
                .map(r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
                .collect(Collectors.toList());

        final MavenDownloader downloader = new MavenDownloader(repositories);

        final Set<Artifact> artifactSet = new HashSet<>();

        ChannelManifest manifest;
        if (channel.getManifestCoordinate().getUrl() != null) {
            manifest = ChannelManifestMapper.from(channel.getManifestCoordinate().getUrl());
        } else {
            final MavenCoordinate coord = channel.getManifestCoordinate().getMaven();
            final Artifact manifestArtifact = downloader.downloadManifest(ChannelManifestCoordinate.create(null, coord));

            System.out.println("Using manifest: " + manifestArtifact);

            manifest = ChannelManifestMapper.from(manifestArtifact.getFile().toURI().toURL());

            artifactSet.add(manifestArtifact);
        }

        // get GAVs of feature-packs;
        if (featurePacks.isEmpty()) {
            System.out.println("Detecting feature packs");
            featurePacks.addAll(channelFeaturePackResolver.findFeaturePacks(manifest.getStreams(), repositories));
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

        artifactSet.addAll(detectArtifactsInFeaturePacks(manifest, downloader));

        System.out.println("Downloading");

        // download and deploy the artifact


        downloader.downloadAndDeploy(artifactSet, repositoryPath, includeSources, includePoms);

        // TODO check that all the artifacts in the manifest were downloaded
        final Set<String> downloaded = artifactSet.stream()
                .map(a -> a.getGroupId() + ":" + a.getArtifactId())
                .collect(Collectors.toSet());
        final Set<String> requested = manifest.getStreams().stream()
                .map(s -> s.getGroupId() + ":" + s.getArtifactId())
                .collect(Collectors.toSet());

        requested.removeAll(downloaded);

        if (!requested.isEmpty()) {
            System.out.println("WARNING: Following streams defined in the manifest were not resolved:");
            requested.forEach(ga -> System.out.println("  * " + ga));
        }

        // download parent poms
        if (includePoms) {
            final Stack<Path> poms = new Stack<>();
            final Set<Artifact> pomArtifacts = new HashSet<>();
            Files.walkFileTree(repositoryPath, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.getFileName().toString().endsWith(".pom")) {
                        return FileVisitResult.CONTINUE;
                    }
                    poms.push(file);
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

            while (!poms.isEmpty()) {
                final Path file = poms.pop();
                try {
                    final Model model = new MavenXpp3Reader().read(new FileInputStream(file.toFile()));
                    final Parent parentModel = model.getParent();
                    if (parentModel != null) {
                        final String groupId = parentModel.getGroupId();
                        final String artifactId = parentModel.getArtifactId();
                        final String version = parentModel.getVersion();

                        System.out.printf("Resolving pom: %s:%s:%s%n", groupId, artifactId, version);
                        final Path parent = downloader.download(groupId, artifactId, null, "pom", version).toPath();
                        poms.push(parent);
                        pomArtifacts.add(new DefaultArtifact(
                                groupId,
                                artifactId,
                                null,
                                "pom",
                                version,
                                null,
                                parent.toFile()
                        ));
                    }

//                    if (model.getDependencyManagement() != null) {
//                        // TODO: resolve versions
//                        for (Dependency dependency : model.getDependencyManagement().getDependencies()) {
//                            if (dependency.getScope() != null && dependency.getScope().equals("import")) {
//                                final String groupId = dependency.getGroupId();
//                                final String artifactId = dependency.getArtifactId();
//                                final String version = dependency.getVersion();
//                                System.out.printf("Resolving pom: %s:%s:%s%n", groupId, artifactId, version);
//                                final Path dep = downloader.download(groupId, artifactId, null, "pom", version).toPath();
//                                poms.push(dep);
//                                pomArtifacts.add(new DefaultArtifact(
//                                        groupId,
//                                        artifactId,
//                                        null,
//                                        "pom",
//                                        version,
//                                        null,
//                                        dep.toFile()
//                                ));
//                            }
//                        }
//                    }

                } catch (XmlPullParserException e) {
                    throw new RuntimeException(e);
                }
            }
            downloader.downloadAndDeploy(pomArtifacts, repositoryPath, false, false);

            if (artifactList) {
                final TreeSet<String> gavs = new TreeSet<>();

                artifactSet.stream().forEach(a->{
                        gavs.add(String.format("%s:%s:%s:%s:%s", a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getVersion(), "compile"));
                    });
                pomArtifacts.stream().forEach(a->{
                    gavs.add(String.format("%s:%s:%s:%s:%s", a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getVersion(), "compile"));
                });

                try (PrintWriter pw = new PrintWriter(new FileWriter("artifact-list"))) {
                    gavs.forEach(pw::println);
                }
            }
        }

        return ReturnCodes.SUCCESS;
    }

    private Set<Artifact> detectArtifactsInFeaturePacks(ChannelManifest manifest, MavenDownloader downloader) throws ProvisioningException, ArtifactResolutionException, IOException {
        final Set<Artifact> artifactSet = new HashSet<>();
        for (String featurePackGA : featurePacks) {
            final Optional<Stream> fpStream = manifest.findStreamFor(featurePackGA.split(":")[0], featurePackGA.split(":")[1]);
            if (fpStream.isEmpty()) {
                throw new RuntimeException("The feature pack " + featurePackGA + " cannot be found in the channel.");
            }

            if (fpMapper == FpMapperValues.ZIP) {
                final File zipFile = downloader.download(
                        featurePackGA.split(":")[0],
                        featurePackGA.split(":")[1],
                        null,
                        "zip",
                        fpStream.get().getVersion()
                );

                artifactSet.addAll(FeaturePackUtils.getArtifactsFromFeaturePackZip(zipFile, manifest));
                artifactSet.add(zipMavenArtifact(featurePackGA, fpStream));
            } else {
                final File artifactListFile = downloader.download(
                        featurePackGA.split(":")[0],
                        featurePackGA.split(":")[1],
                        "artifact-list",
                        "txt",
                        fpStream.get().getVersion()
                );

                FileUtils.readLines(artifactListFile, StandardCharsets.UTF_8).stream()
                        .map(l->l.split(",")[1])
                        .map(CoordUtils::fromLocalPath)
                        .map(a-> manifest.findStreamFor(a.getGroupId(), a.getArtifactId()).map(s->a.setVersion(s.getVersion())))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(artifactSet::add);
                artifactSet.add(zipMavenArtifact(featurePackGA, fpStream));
            }
        }
        return artifactSet;
    }

    private static DefaultArtifact zipMavenArtifact(String featurePackGA, Optional<Stream> fpStream) {
        return new DefaultArtifact(
                featurePackGA.split(":")[0],
                featurePackGA.split(":")[1],
                null,
                "zip",
                fpStream.get().getVersion()
        );
    }
}
