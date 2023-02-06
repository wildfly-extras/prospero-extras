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

package org.wildfly.prospero.extras.manifest.diff;

import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.bundle.create.CreateBundleCommand;
import org.wildfly.prospero.extras.converters.ManifestConverter;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "manifest-diff")
public class ManifestsDiffCommand implements Callable<Integer> {
    private static final Logger LOG = Logger.getLogger(CreateBundleCommand.class);

    @CommandLine.Parameters(index = "0", converter = ManifestConverter.class)
    private ChannelManifest manifestOne;

    @CommandLine.Parameters(index = "1", converter = ManifestConverter.class)
    private ChannelManifest manifestTwo;

    @Override
    public Integer call() throws Exception {


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
