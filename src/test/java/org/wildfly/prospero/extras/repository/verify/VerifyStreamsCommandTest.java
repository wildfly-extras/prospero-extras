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

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifyStreamsCommandTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final Set<String> served = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> statusOverrides = new ConcurrentHashMap<>();
    private final Set<String> authHeaders = ConcurrentHashMap.newKeySet();

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            final String path = exchange.getRequestURI().getPath();
            final String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                authHeaders.add(auth);
            }
            final int status;
            if (!exchange.getRequestMethod().equals("HEAD")) {
                status = 405;
            } else if (statusOverrides.containsKey(path)) {
                status = statusOverrides.get(path);
            } else {
                status = served.contains(path) ? 200 : 404;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stopServer() {
        server.stop(0);
    }

    @Test
    public void allPresentInHttpRepository() {
        served.add("/org/foo/bar/1.0/bar-1.0.pom");
        served.add("/org/foo/baz/2.0/baz-2.0.pom");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0"), new Stream("org.foo", "baz", "2.0")),
                List.of(httpRepo()), null);

        assertThat(result.getChecked()).isEqualTo(2);
        assertThat(result.getMissing()).isEmpty();
        assertThat(result.getUnverified()).isEmpty();
    }

    @Test
    public void reportsMissingInHttpRepository() {
        served.add("/org/foo/bar/1.0/bar-1.0.pom");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0"), new Stream("org.foo", "baz", "2.0")),
                List.of(httpRepo()), null);

        assertThat(result.getMissing()).containsExactly("org.foo:baz:2.0");
        assertThat(result.getUnverified()).isEmpty();
    }

    @Test
    public void repositoryUrlWithTrailingSlash() {
        served.add("/org/foo/bar/1.0/bar-1.0.pom");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0")), List.of(httpRepo() + "/"), null);

        assertThat(result.getMissing()).isEmpty();
    }

    @Test
    public void unexpectedStatusIsReportedAsUnverifiedNotMissing() {
        statusOverrides.put("/org/foo/bar/1.0/bar-1.0.pom", 403);

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0")), List.of(httpRepo()), null);

        assertThat(result.getMissing()).isEmpty();
        assertThat(result.getUnverified()).containsOnlyKeys("org.foo:bar:1.0");
        assertThat(result.getUnverified().get("org.foo:bar:1.0")).contains("403");
    }

    @Test
    public void fileRepository() throws Exception {
        final Path repo = tempDir.resolve("repo");
        createPom(repo, "org.foo", "bar", "1.0");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0"), new Stream("org.foo", "baz", "2.0")),
                List.of(repo.toUri().toString()), null);

        assertThat(result.getMissing()).containsExactly("org.foo:baz:2.0");
    }

    @Test
    public void presentInAnyOfMultipleRepositories() throws Exception {
        final Path repo = tempDir.resolve("repo");
        createPom(repo, "org.foo", "bar", "1.0");
        served.add("/org/foo/baz/2.0/baz-2.0.pom");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0"), new Stream("org.foo", "baz", "2.0"),
                        new Stream("org.foo", "missing", "3.0")),
                List.of(repo.toUri().toString(), httpRepo()), null);

        assertThat(result.getChecked()).isEqualTo(3);
        assertThat(result.getMissing()).containsExactly("org.foo:missing:3.0");
        assertThat(result.getUnverified()).isEmpty();
    }

    @Test
    public void failingRepositoryDoesNotHideStreamPresentInAnother() throws Exception {
        final Path repo = tempDir.resolve("repo");
        createPom(repo, "org.foo", "bar", "1.0");
        statusOverrides.put("/org/foo/bar/1.0/bar-1.0.pom", 500);

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0")),
                List.of(httpRepo(), repo.toUri().toString()), null);

        assertThat(result.getMissing()).isEmpty();
        assertThat(result.getUnverified()).isEmpty();
    }

    @Test
    public void streamsWithVersionPatternAreSkipped() {
        served.add("/org/foo/bar/1.0/bar-1.0.pom");

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0"), new Stream("org.foo", "baz", Pattern.compile("1\\..*"))),
                List.of(httpRepo()), null);

        assertThat(result.getChecked()).isEqualTo(1);
        assertThat(result.getSkippedPatternStreams()).isEqualTo(1);
        assertThat(result.getMissing()).isEmpty();
    }

    @Test
    public void credentialsFromMavenSettingsAreSent() {
        served.add("/org/foo/bar/1.0/bar-1.0.pom");

        final Repository repository = new Repository();
        repository.setId("secured");
        repository.setUrl(httpRepo());
        final Profile profile = new Profile();
        profile.setId("p");
        profile.addRepository(repository);
        final Server mavenServer = new Server();
        mavenServer.setId("secured");
        mavenServer.setPassword("s3cret");
        final Settings settings = new Settings();
        settings.addProfile(profile);
        settings.addServer(mavenServer);

        final VerifyStreamsCommand.Result result = VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0")), List.of(httpRepo()), settings);

        assertThat(result.getMissing()).isEmpty();
        assertThat(authHeaders).containsExactly("Bearer s3cret");
    }

    @Test
    public void unsupportedProtocolIsRejected() {
        assertThatThrownBy(() -> VerifyStreamsCommand.verify(
                List.of(new Stream("org.foo", "bar", "1.0")), List.of("ftp://example.org/repo"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ftp://example.org/repo");
    }

    private String httpRepo() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private static void createPom(Path repo, String groupId, String artifactId, String version) throws IOException {
        final Path dir = repo.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(artifactId + "-" + version + ".pom"), "");
    }
}
