package org.wildfly.prospero.extras;

import org.wildfly.prospero.extras.manifest.ManifestOperations;
import org.wildfly.prospero.extras.manifest.ManifestOperationsFactory;

public class ProsperoExtras {

    public static ManifestOperations manifestOperations() {
        return ManifestOperationsFactory.getNewInstance();
    }
}