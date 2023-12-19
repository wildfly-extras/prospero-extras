package org.wildfly.prospero.extras.repository.create;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.logging.Logger;
import org.wildfly.channel.Stream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
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
            throw new RuntimeException(e);
        }
        return null;
    }

    private Path downloadZip(String zipUrl) throws IOException {
        Path tempFile = Files.createTempFile("candidate", "zip");

        final URL url = new URL(zipUrl);
        final URLConnection conn = url.openConnection();
        conn.connect();

        try(InputStream inputStream = conn.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(tempFile.toFile())) {

            int BUFFER_SIZE = 4096;
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

//        IOUtils.copy(new URL(zipUrl), tempFile.toFile(), );
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
                    e.printStackTrace();
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
