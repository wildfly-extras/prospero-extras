package org.wildfly.prospero.extras.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * Retries requests that received an HTTP 5xx response.
 */
class ServerErrorRetryStrategy implements ServiceUnavailableRetryStrategy {

    private final int maxRetries;
    private final long retryInterval;

    ServerErrorRetryStrategy(int maxRetries, long retryInterval) {
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        final int status = response.getStatusLine().getStatusCode();
        return executionCount <= maxRetries && status >= 500 && status < 600;
    }

    @Override
    public long getRetryInterval() {
        return retryInterval;
    }
}
