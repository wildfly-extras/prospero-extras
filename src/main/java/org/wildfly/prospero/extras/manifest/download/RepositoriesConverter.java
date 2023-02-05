package org.wildfly.prospero.extras.manifest.download;

import org.eclipse.aether.repository.RemoteRepository;
import picocli.CommandLine;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class RepositoriesConverter implements CommandLine.ITypeConverter<RemoteRepository> {

    private static AtomicInteger counter = new AtomicInteger();
    @Override
    public RemoteRepository convert(String spec) throws Exception {
        if (spec.contains("::")) {
            final String[] splitSpec = spec.split("::");
            final String id = splitSpec[0];
            final URL url = new URL(splitSpec[1]);
            return new RemoteRepository.Builder(id, "default", url.toExternalForm()).build();
        } else {
            final URL url = new URL(spec);
            final String id = "repo-" + counter.getAndIncrement();
            return new RemoteRepository.Builder(id, "default", url.toExternalForm()).build();
        }
    }
}
