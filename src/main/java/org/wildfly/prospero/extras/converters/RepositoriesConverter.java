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
