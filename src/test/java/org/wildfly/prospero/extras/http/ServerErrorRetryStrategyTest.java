package org.wildfly.prospero.extras.http;

import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerErrorRetryStrategyTest {

    private final ServerErrorRetryStrategy strategy = new ServerErrorRetryStrategy(3, 100);

    @Test
    public void retriesServerErrors() {
        assertThat(strategy.retryRequest(response(500), 1, new BasicHttpContext())).isTrue();
        assertThat(strategy.retryRequest(response(503), 3, new BasicHttpContext())).isTrue();
    }

    @Test
    public void givesUpAfterMaxRetries() {
        assertThat(strategy.retryRequest(response(503), 4, new BasicHttpContext())).isFalse();
    }

    @Test
    public void doesNotRetryOtherStatuses() {
        assertThat(strategy.retryRequest(response(200), 1, new BasicHttpContext())).isFalse();
        assertThat(strategy.retryRequest(response(404), 1, new BasicHttpContext())).isFalse();
    }

    @Test
    public void usesConfiguredInterval() {
        assertThat(strategy.getRetryInterval()).isEqualTo(100);
    }

    private static BasicHttpResponse response(int status) {
        return new BasicHttpResponse(HttpVersion.HTTP_1_1, status, null);
    }
}
