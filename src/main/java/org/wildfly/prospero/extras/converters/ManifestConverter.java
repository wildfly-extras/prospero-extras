package org.wildfly.prospero.extras.converters;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

public class ManifestConverter implements CommandLine.ITypeConverter<ChannelManifest> {
    @Override
    public ChannelManifest convert(String pathString) throws Exception {
        final Path path = Path.of(pathString);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Path " + pathString + " does not exist");
        }

        return ChannelManifestMapper.from(path.toUri().toURL());
    }
}
