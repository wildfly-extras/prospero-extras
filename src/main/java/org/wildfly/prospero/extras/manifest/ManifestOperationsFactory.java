package org.wildfly.prospero.extras.manifest;

public abstract class ManifestOperationsFactory {

    public static ManifestOperations getNewInstance() {
        return new ManifestOperationsImpl();
    }
}
