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
