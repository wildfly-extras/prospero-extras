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

package org.wildfly.prospero.extras.repository.verify;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.extras.ReturnCodes;
import org.wildfly.prospero.extras.converters.ManifestConverter;
import org.wildfly.prospero.extras.http.AuthenticatingHttpClientBuilder;
import org.wildfly.prospero.extras.shared.CommandWithHelp;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verifies that the POM of every stream in a manifest is available in at least one of the given Maven repositories.
 *
 * Remote repositories are checked using HTTP HEAD requests, so no artifacts are downloaded. File repositories are
 * checked by looking for the file.
 *
 * The list of missing GAVs is printed to stdout, everything else (progress, warnings, summary) is printed to stderr.
 */
@CommandLine.Command(name = "verify-streams")
public class VerifyStreamsCommand extends CommandWithHelp {

    private static final int PARALLELISM = Integer.getInteger("wildfly.prospero.verify_threads", 20);

    @CommandLine.Option(names = {"--manifest"}, converter = ManifestConverter.class, required = true)
    private ChannelManifest manifest;

    @CommandLine.Option(names = {"--repositories"}, required = true, split = ",")
    private List<String> repositories;

    @CommandLine.Option(names = {"--maven-settings"},
            description = "Maven settings.xml used to look up credentials of the repositories.")
    private Path mavenSettingsPath;

    @Override
    public Integer call() throws Exception {
        final Settings mavenSettings = mavenSettingsPath == null ? null
                : new DefaultSettingsReader().read(mavenSettingsPath.toFile(), null);

        final Result result;
        try {
            result = verify(manifest.getStreams(), repositories, mavenSettings);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        if (result.getSkippedPatternStreams() > 0) {
            System.err.println("WARNING: " + result.getSkippedPatternStreams()
                    + " stream(s) use a version pattern and were not verified.");
        }
        result.getUnverified().forEach((gav, reason) ->
                System.err.println("WARNING: Unable to verify " + gav + ": " + reason));

        result.getMissing().forEach(System.out::println);

        if (!result.getMissing().isEmpty()) {
            System.err.println(result.getMissing().size() + " of " + result.getChecked() + " GAVs are missing.");
        }
        if (result.getMissing().isEmpty() && result.getUnverified().isEmpty()) {
            System.err.println("All " + result.getChecked() + " GAVs are present.");
            return ReturnCodes.SUCCESS;
        }
        return ReturnCodes.ERROR;
    }

    /**
     * Checks presence of the POM of each stream in the repositories. A stream is present if any of the repositories
     * contains it. A stream is reported as missing if all repositories answered and none contains it. If some
     * repository could not be checked (e.g. unexpected HTTP status) and no repository contains the stream, the stream
     * is reported as unverified.
     *
     * @param streams streams to verify. Streams using version pattern are skipped.
     * @param repositories repository URLs (http, https or file)
     * @param mavenSettings optional Maven settings used for HTTP authentication
     * @return result of the verification
     * @throws IllegalArgumentException if a repository URL is not valid or its protocol is not supported
     */
    public static Result verify(Collection<Stream> streams, List<String> repositories, Settings mavenSettings) {
        final List<URI> repositoryUris = new ArrayList<>();
        for (String repository : repositories) {
            repositoryUris.add(parseRepository(repository));
        }

        int skippedPatterns = 0;
        final List<Stream> toCheck = new ArrayList<>();
        for (Stream stream : streams) {
            if (stream.getVersion() == null) {
                skippedPatterns++;
            } else {
                toCheck.add(stream);
            }
        }

        final List<String> missing = new ArrayList<>();
        final Map<String, String> unverified = new LinkedHashMap<>();

        final ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(PARALLELISM, toCheck.size())));
        try (CloseableHttpClient client = AuthenticatingHttpClientBuilder.build(mavenSettings)) {
            final List<CompletableFuture<Outcome>> tasks = new ArrayList<>();
            for (Stream stream : toCheck) {
                tasks.add(CompletableFuture.supplyAsync(() -> check(client, repositoryUris, stream), executor));
            }

            // collect in the manifest order to get stable output
            for (CompletableFuture<Outcome> task : tasks) {
                final Outcome outcome = task.join();
                if (outcome.status == Status.ABSENT) {
                    missing.add(outcome.gav);
                } else if (outcome.status == Status.UNVERIFIED) {
                    unverified.put(outcome.gav, outcome.detail);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to close the HTTP client", e);
        } finally {
            executor.shutdownNow();
        }

        return new Result(toCheck.size(), missing, unverified, skippedPatterns);
    }

    private static URI parseRepository(String repository) {
        final URI uri;
        try {
            uri = new URI(repository.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid repository URL: " + repository, e);
        }
        final String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            try {
                Path.of(uri);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid repository URL: " + repository, e);
            }
        } else if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported repository protocol: " + repository
                    + ". Only http, https and file are supported.");
        }
        return uri;
    }

    private static Outcome check(CloseableHttpClient client, List<URI> repositories, Stream stream) {
        final String gav = stream.getGroupId() + ":" + stream.getArtifactId() + ":" + stream.getVersion();
        final String relativePath = stream.getGroupId().replace('.', '/') + "/" + stream.getArtifactId() + "/"
                + stream.getVersion() + "/" + stream.getArtifactId() + "-" + stream.getVersion() + ".pom";

        final List<String> errors = new ArrayList<>();
        for (URI repository : repositories) {
            try {
                if (exists(client, repository, relativePath)) {
                    return new Outcome(gav, Status.PRESENT, null);
                }
            } catch (IOException | RuntimeException e) {
                errors.add(repository + " - " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            return new Outcome(gav, Status.ABSENT, null);
        }
        return new Outcome(gav, Status.UNVERIFIED, String.join("; ", errors));
    }

    private static boolean exists(CloseableHttpClient client, URI repository, String relativePath) throws IOException {
        if ("file".equals(repository.getScheme())) {
            return Files.isRegularFile(Path.of(repository).resolve(relativePath));
        }

        final String base = repository.toString();
        final String url = (base.endsWith("/") ? base : base + "/") + relativePath;
        try (CloseableHttpResponse response = client.execute(new HttpHead(url))) {
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return true;
            } else if (statusCode == 404) {
                return false;
            }
            throw new IOException("Unexpected HTTP status " + statusCode + " for " + url);
        }
    }

    private enum Status {
        PRESENT, ABSENT, UNVERIFIED
    }

    private static final class Outcome {
        private final String gav;
        private final Status status;
        private final String detail;

        private Outcome(String gav, Status status, String detail) {
            this.gav = gav;
            this.status = status;
            this.detail = detail;
        }
    }

    /**
     * Result of the streams verification.
     */
    public static class Result {
        private final int checked;
        private final List<String> missing;
        private final Map<String, String> unverified;
        private final int skippedPatternStreams;

        private Result(int checked, List<String> missing, Map<String, String> unverified, int skippedPatternStreams) {
            this.checked = checked;
            this.missing = missing;
            this.unverified = unverified;
            this.skippedPatternStreams = skippedPatternStreams;
        }

        /**
         * @return number of streams that were checked (excluding streams using version patterns)
         */
        public int getChecked() {
            return checked;
        }

        /**
         * @return GAVs that were not found in any of the repositories
         */
        public List<String> getMissing() {
            return missing;
        }

        /**
         * @return GAVs that were not found and could not be verified in some repository, with the reason
         */
        public Map<String, String> getUnverified() {
            return unverified;
        }

        /**
         * @return number of streams skipped because they use version pattern instead of a version
         */
        public int getSkippedPatternStreams() {
            return skippedPatternStreams;
        }
    }
}
