package org.wildfly.prospero.extras.repository.create;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CoordUtils {

    private static final Logger LOG = Logger.getLogger(CoordUtils.class);

    static DefaultArtifact fromLocalPath(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            throw new RuntimeException("Invalid path " + path + ". Unable to convert to maven coordinate.");
        }
        final String filename = path.substring(index + 1);
        String rest = path.substring(0, index);
        if (LOG.isTraceEnabled()) {
            LOG.tracef("Found filename %s and remaining path is %s", filename, rest);
        }

        index = rest.lastIndexOf('/');
        if (index < 0) {
            throw new RuntimeException("Invalid path " + path + ". Unable to convert to maven coordinate.");
        }
        final String version = rest.substring(index + 1);
        rest = path.substring(0, index);
        if (LOG.isTraceEnabled()) {
            LOG.tracef("Found version %s and remaining path is %s", version, rest);
        }

        final Pattern filenamePattern = Pattern.compile("(.*)-" + version + "-?(.*)\\.(.*)$");
        final Matcher matcher = filenamePattern.matcher(filename);
        if (!matcher.matches()) {
            throw new RuntimeException("Unable to parse filename " + filename);
        }
        final String artifactId = matcher.group(1);
        final String classifier = matcher.group(2);
        final String extension = matcher.group(3);

        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length()-1);
        }
        final String groupId = rest.replace("/" + artifactId.replace('.', '/'), "").replace('/', '.');

        if (LOG.isDebugEnabled()) {
            LOG.debug("groupId " + groupId);
            LOG.debug("artifactId " + artifactId);
            LOG.debug("classifier " + classifier);
            LOG.debug("extension " + extension);
        }

        return new DefaultArtifact(
                groupId,
                artifactId,
                classifier,
                extension,
                version
        );
    }
}
