package org.wildfly.prospero.extras.manifest.subtract;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.shared.CommandWithHelp;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CommandLine.Command(name = "manifest-subtract")
public class ManifestSubtractCommand extends CommandWithHelp {

    private static final Pattern EXCLUSION_PATTERN = Pattern.compile("[\\w-_.*]*:[\\w-_.*]*");

    @CommandLine.Parameters(index = "0", descriptionKey = "parameterOne")
    Path manifestOne;

    @CommandLine.Parameters(index = "1", descriptionKey = "parameterTwo")
    Path manifestTwo;

    @CommandLine.Option(names = "--exclude", split = ",")
    List<String> exclusions = Collections.emptyList();

    @Override
    public Integer call() throws Exception {
        final ChannelManifest manifestOne = ChannelManifestMapper.from(this.manifestOne.toUri().toURL());
        final ChannelManifest manifestTwo = ChannelManifestMapper.from(this.manifestTwo.toUri().toURL());

        final ChannelManifest res = subtract(manifestOne, manifestTwo, exclusions);

        System.out.println(ChannelManifestMapper.toYaml(res));

        return ReturnCodes.SUCCESS;
    }

    public static ChannelManifest subtract(ChannelManifest manifestOne, ChannelManifest manifestTwo, List<String> exclusions) {
        Objects.requireNonNull(manifestOne);
        Objects.requireNonNull(manifestTwo);
        Objects.requireNonNull(exclusions);

        final Optional<String> illegalPattern = exclusions.stream()
                .filter(e -> !EXCLUSION_PATTERN.matcher(e).matches())
                .findFirst();
        if (illegalPattern.isPresent()) {
            throw new IllegalArgumentException("Exclusion [" + illegalPattern.get() + "] has invalid format ([\\w-_.*]*:[\\w-_.*]*).");
        }

        final Set<String> groupExclusions = exclusions.stream()
                .map(String::trim)
                .filter(e -> e.endsWith(":*"))
                .map(e -> e.substring(0, e.length() - 2))
                .collect(Collectors.toSet());

        final Set<String> groupArtifactExclusions = exclusions.stream()
                .map(String::trim)
                .filter(e -> !e.endsWith(":*"))
                .collect(Collectors.toSet());

        final Set<String> streamKeys = manifestTwo.getStreams().stream()
                .filter(s -> !groupExclusions.contains(s.getGroupId()))
                .filter(s -> !groupArtifactExclusions.contains(s.getGroupId() + ":" + s.getArtifactId()))
                .map(s -> getKey(s))
                .collect(Collectors.toSet());


        final List<Stream> filteredStreams = manifestOne.getStreams().stream()
                .filter(s -> !streamKeys.contains(getKey(s)))
                .collect(Collectors.toList());


        return new ChannelManifest(manifestOne.getSchemaVersion(), manifestOne.getName(), manifestOne.getId(),
                manifestOne.getDescription(), manifestOne.getManifestRequirements(), filteredStreams);
    }

    private static String getKey(Stream s) {
        return s.getGroupId() + ":" + s.getArtifactId();
    }
}
