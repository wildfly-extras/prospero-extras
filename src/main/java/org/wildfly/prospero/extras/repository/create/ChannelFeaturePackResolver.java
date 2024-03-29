package org.wildfly.prospero.extras.repository.create;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.logging.Logger;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class ChannelFeaturePackResolver {
    private static final Logger LOG = Logger.getLogger(DownloadRepositoryCommand.class);
    private static AtomicInteger counter = new AtomicInteger(0);
    protected static final int DETECTION_PARALLERLISM = Integer.getInteger("wildfly.prospero.fp.detect_threads", 20);

    List<String> findFeaturePacks(Collection<Stream> streams, List<RemoteRepository> repositories) throws IOException {
        final Set<String> featurePacks = new HashSet<>();

        ExecutorService executorService = null;

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            executorService = Executors.newWorkStealingPool(DETECTION_PARALLERLISM);

            final List<CompletableFuture<String>> allTasks = new ArrayList<>();
            for (Stream s : streams) {
                final CompletableFuture<String> cf = new CompletableFuture<>();
                executorService.submit(new UrlCheck(repositories, client, s, cf));
                allTasks.add(cf);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Waiting to finish " + allTasks.size() + " tasks");
            }

            CompletableFuture.allOf(allTasks.toArray(new CompletableFuture[]{})).join();
            for (CompletableFuture<String> task : allTasks) {
                final String value = task.get();
                if (value != null) {
                    featurePacks.add(value);
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("All tasks complete");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (executorService != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Shutting down the executor");
                }
                System.out.println("Shutting down the executor");
                executorService.shutdownNow();
            }
        }
        return new ArrayList<>(featurePacks);
    }

    private String getExistingZipUrl(CloseableHttpClient client, String baseUrl, Stream s) {
        final String url = baseUrl + "/" + s.getGroupId().replaceAll("\\.", "/") + "/" +
                s.getArtifactId() + "/" + s.getVersion() + "/" + s.getArtifactId() + "-" + s.getVersion() + ".zip";
        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking " + s + " for feature packs. Trying " + url);
        }
        final HttpHead httpHead = new HttpHead(url);
        try (CloseableHttpResponse res = client.execute(httpHead)) {
            if (res.getStatusLine().getStatusCode() == 200) {
                return url;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }

    private Path downloadZip(String zipUrl) throws IOException {
        Path tempFile = Files.createTempFile("candidate", "zip");

        try (CloseableHttpClient client = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build()) {
            final HttpGet get = new HttpGet(zipUrl);
            client.execute(get, httpResponse -> {
                FileUtils.copyInputStreamToFile(httpResponse.getEntity().getContent(), tempFile.toFile());
                return tempFile.toFile();
            });
        }

        return tempFile;
    }

    private class UrlCheck implements Runnable {

        private final List<RemoteRepository> repositories;
        private final CloseableHttpClient client;
        private final Stream stream;
        private final CompletableFuture<String> cf;

        UrlCheck(List<RemoteRepository> repositories, CloseableHttpClient client, Stream stream, CompletableFuture<String> cf) {
            this.repositories = repositories;
            this.client = client;
            this.stream = stream;
            this.cf = cf;
        }

        @Override
        public void run() {
            String zipUrl = null;
            String fpGa = null;

            for (RemoteRepository repo : repositories) {
                if (repo.getUrl().startsWith("file")) {
                    // TODO: support file
                    continue;
                }
                final String baseUrl = repo.getUrl();
                zipUrl = getExistingZipUrl(client, baseUrl, stream);
                if (zipUrl != null) {
                    break;
                }
            }

            if (zipUrl != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found possible zip: " + zipUrl + ". Downloading to verify.");
                }
                // download zip
                Path tempFile = null;
                try {
                    tempFile = downloadZip(zipUrl);
                    if (FeaturePackUtils.isFeaturePack(tempFile)) {
                        LOG.debug("Found feature pack " + stream);
                        fpGa = stream.getGroupId() + ":" + stream.getArtifactId();
                    }
                } catch (IOException e) {
                    LOG.warn("Unable to process a zip file: " + zipUrl + " Ignoring the file", e);
                    cf.completeExceptionally(e);
                } finally {
                    if (tempFile != null) {
                        try {
                            Files.delete(tempFile);
                        } catch (IOException e) {
                            LOG.error("Unable to remove temporary download: " + tempFile, e);
                        }
                    }
                }
            }
            cf.complete(fpGa);
        }
    }
}
