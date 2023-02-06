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

package org.wildfly.prospero.extras.bundle.create;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.logging.Logger;
import org.wildfly.prospero.extras.converters.RepositoriesConverter;
import org.wildfly.prospero.promotion.ArtifactBundle;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.wildfly.installationmanager.MavenOptions.LOCAL_MAVEN_REPO;

@CommandLine.Command(name = "create-bundle")
public class CreateBundleCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(CreateBundleCommand.class);

    @CommandLine.Option(names={"--out"}, required = true)
    private Path bundlePath;

    @CommandLine.Option(names={"--artifact", "--artifacts"}, required = true)
    private List<String> artifacts = new ArrayList<>();

    @CommandLine.Option(names={"--repository", "--repositories"}, converter = RepositoriesConverter.class)
    private List<RemoteRepository> repositories = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        LOG.info("Building artifact requests");
        List<ArtifactRequest> requests = new ArrayList<>();

        for (String artifactSpec : artifacts) {
            final String[] gav = artifactSpec.split(":");
            if (gav.length < 3) {
                LOG.error("The artifact GAV is expected to contain at least 3 elements");
                return -2;
            }

            String version = gav[2];
            String classifier = null;
            String extension = "jar";

            if (gav.length == 4) {
                version = gav[3];
                classifier = gav[2];
            }
            if (gav.length == 5) {
                version = gav[4];
                classifier = gav[2];
                extension = gav[3];
            }
            final DefaultArtifact artifact = new DefaultArtifact(gav[0], gav[1], classifier, extension, version, null);
            final ArtifactRequest artifactRequest = new ArtifactRequest(artifact, repositories, null);
            LOG.infof("{groupId: %s, artifactId: %s, classifier: %s, extension %s, version: %s}",
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                    artifact.getExtension(), artifact.getVersion());
            requests.add(artifactRequest);
        }

        LOG.info("Downloading artifacts");
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LOCAL_MAVEN_REPO);
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system, true);
        try {
            final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);

            final List<Artifact> downloadedArtifacts = artifactResults.stream().map(ArtifactResult::getArtifact).collect(Collectors.toList());

            LOG.info("Creating bundle");
            ArtifactBundle.createCustomizationArchive(downloadedArtifacts, bundlePath.toFile());
            return 0;
        } catch (ArtifactResolutionException e) {
            LOG.warn("Unable to find following artifacts:");
            e.getResults().stream()
                    .filter(r -> !r.isResolved())
                    .map(ArtifactResult::getRequest)
                    .forEach(r-> LOG.warn(" * " + r.getArtifact()));
            return -1;
        }
    }
}
