package org.wildfly.prospero.extras.manifest.toopen;

import org.apache.commons.io.FileUtils;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(name = "manifest-to-open")
public class ConvertToOpenCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--source-manifest", "-s"})
    private Path sourceManifestPath;

    @CommandLine.Option(names = {"--output-manifest", "-o"})
    private Path outputManifestPath;

    public static void main(String[] args) throws Exception {
        new ConvertToOpenCommand().call();
    }
    @Override
    public Integer call() throws Exception {

        final Path sourceManifestPath = Path.of("/Users/spyrkob/workspaces/set/prospero/prospero-extras/eap8-manifest-proposed.yaml");

        final ChannelManifest sourceManifest = ChannelManifestMapper
                .fromString(FileUtils.readFileToString(sourceManifestPath.toFile(), StandardCharsets.UTF_8));

        final Map<String, List<Stream>> streamsByGroup = new TreeMap<>();
        for (Stream stream : sourceManifest.getStreams()) {
            if (!streamsByGroup.containsKey(stream.getGroupId())) {
                streamsByGroup.put(stream.getGroupId(), new ArrayList<>());
            }
            streamsByGroup.get(stream.getGroupId()).add(stream);
        }

        final List<Stream> res = new ArrayList<>();
        for (Map.Entry<String, List<Stream>> entry : streamsByGroup.entrySet()) {

            final Stream firstStream = entry.getValue().get(0);
            if (entry.getValue().stream().allMatch(s->s.getVersion().equals(firstStream.getVersion()))) {
                res.add(new Stream(
                        firstStream.getGroupId(),
                        "*",
                        toPattern(firstStream.getVersion())
                ));
            } else {
                for (Stream stream : entry.getValue()) {
                    res.add(new Stream(
                            stream.getGroupId(),
                            stream.getArtifactId(),
                            toPattern(stream.getVersion())
                    ));
                }
            }
        }

        final ChannelManifest openManifest = new ChannelManifest(
                sourceManifest.getSchemaVersion(),
                sourceManifest.getName(),
                sourceManifest.getDescription(),
                res);

        Files.writeString(outputManifestPath, ChannelManifestMapper.toYaml(openManifest), StandardCharsets.UTF_8);

        return 0;
    }

    private static Pattern toPattern(String version) {
        final Pattern versionPattern = Pattern.compile("\\d+\\.\\d+\\.(.*)[.|-]redhat-(\\d+)");
        final Matcher matcher = versionPattern.matcher(version);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("Version %s doesn't match", version));
        }
        // TODO try converting this using the pattern
        final MatchResult matchResult = matcher.toMatchResult();
        final int microStart = matchResult.start(0);
        final int microEnd = matchResult.end(0);
        final int suffixStart = matchResult.start(1);
        final int suffixEnd = matchResult.end(1);

//        version.substring(0, microStart) + "\\d+" + version.substring(0, microEnd)

        final String[] parts = version.split("\\.");

        return Pattern.compile(parts[0] + "\\." + parts[1] + "\\." + "\\d+" + ".*redhat-.*");
    }
}
