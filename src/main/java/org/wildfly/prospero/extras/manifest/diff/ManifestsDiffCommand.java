package org.wildfly.prospero.extras.manifest.diff;

import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.bundle.create.CreateBundleCommand;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "manifest-diff")
public class ManifestsDiffCommand implements Callable<Integer> {
    private static final Logger LOG = Logger.getLogger(CreateBundleCommand.class);

    @CommandLine.Parameters(index = "0")
    private Path manifestPathOne;

    @CommandLine.Parameters(index = "1")
    private Path manifestPathTwo;

    @Override
    public Integer call() throws Exception {
        System.out.printf("Comparing %s:%s%n", manifestPathOne, manifestPathTwo);
        final ChannelManifest manifestOne = ChannelManifestMapper.from(this.manifestPathOne.toUri().toURL());
        final ChannelManifest manifestTwo = ChannelManifestMapper.from(this.manifestPathTwo.toUri().toURL());


        List<ArtifactChange> changes = manifestDiff(manifestOne, manifestTwo);

        System.out.println();
        changes.forEach(c->{
            switch (c.getStatus()) {
                case REMOVED:
                    System.out.printf("%s%n", c.getArtifactName());
                    System.out.printf(" - %s:%n", c.getOldVersion());
                    break;
                case INSTALLED:
                    System.out.printf("%s%n", c.getArtifactName());
                    System.out.printf(" + %s%n", c.getNewVersion());
                    break;
                case UPDATED:
                    System.out.printf("%s:%n", c.getArtifactName());
                    System.out.printf(" - %s%n", c.getOldVersion());
                    System.out.printf(" + %s%n", c.getNewVersion());
                    break;
            }
        });

        return ReturnCodes.SUCCESS;
    }

    public static List<ArtifactChange> manifestDiff(ChannelManifest manifestOne, ChannelManifest manifestTwo) {
        Map<String, String> versionMapOne = streamToMap(manifestOne);

        Map<String, String> versionMapTwo = streamToMap(manifestTwo);

        for (String key: versionMapTwo.keySet()) {
            if (!versionMapOne.containsKey(key)) {
                versionMapOne.put(key, null);
            }
        }

        List<ArtifactChange> changes = new ArrayList<>();

        for (String key : versionMapOne.keySet()) {
            final String versionOne = versionMapOne.get(key);
            final String versionTwo = versionMapTwo.get(key);
            if (!versionMapTwo.containsKey(key)) {
                changes.add(new ArtifactChange(versionOne, null, key, ArtifactChange.Status.REMOVED));
            } else if (versionMapOne.get(key) == null) {
                changes.add(new ArtifactChange(null, versionTwo, key, ArtifactChange.Status.INSTALLED));
            } else if (!versionTwo.equals(versionOne)) {
                changes.add(new ArtifactChange(versionOne, versionTwo, key, ArtifactChange.Status.UPDATED));
            }
        }
        return changes;
    }

    private static Map<String, String> streamToMap(ChannelManifest manifestOne) {
        final Collection<Stream> streamsOne = manifestOne.getStreams();
        Map<String, String> versionMapOne = new TreeMap<>();
        for (Stream stream : streamsOne) {
            String spec = String.format("%s:%s", stream.getGroupId(), stream.getArtifactId());
            versionMapOne.put(spec, stream.getVersion());
        }
        return versionMapOne;
    }
}
