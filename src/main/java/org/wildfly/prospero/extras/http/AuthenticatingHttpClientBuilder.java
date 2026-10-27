package org.wildfly.prospero.extras.http;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import javax.net.ssl.SSLException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

public class AuthenticatingHttpClientBuilder {

    private static final String HTTP_AUTH_TOKEN_PROPERTY = "prospero.extras.http.auth.token";

    /** Connection timeout in milliseconds. */
    static final String CONNECT_TIMEOUT_PROPERTY = "prospero.extras.http.connect.timeout";
    /** Socket (read) timeout in milliseconds. */
    static final String SOCKET_TIMEOUT_PROPERTY = "prospero.extras.http.socket.timeout";
    /** Maximum number of retries on I/O errors, timeouts and HTTP 5xx responses. */
    static final String MAX_RETRIES_PROPERTY = "prospero.extras.http.retries";
    /** Delay between retries of a request that received an HTTP 5xx response, in milliseconds. */
    static final String RETRY_INTERVAL_PROPERTY = "prospero.extras.http.retry.interval";

    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;
    private static final int DEFAULT_SOCKET_TIMEOUT = 60_000;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_RETRY_INTERVAL = 2_000;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 20;

    public static CloseableHttpClient build(Settings mavenSettings) {
        final int maxRetries = Integer.getInteger(MAX_RETRIES_PROPERTY, DEFAULT_MAX_RETRIES);
        final int retryInterval = Integer.getInteger(RETRY_INTERVAL_PROPERTY, DEFAULT_RETRY_INTERVAL);

        HttpClientBuilder builder = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                // the default (2 connections per host) would serialize parallel requests to a single repository
                .setMaxConnPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE)
                .setMaxConnTotal(DEFAULT_MAX_CONNECTIONS_PER_ROUTE)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Integer.getInteger(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT))
                        .setSocketTimeout(Integer.getInteger(SOCKET_TIMEOUT_PROPERTY, DEFAULT_SOCKET_TIMEOUT))
                        .build())
                // unlike the default handler, also retry timeouts and refused connections
                .setRetryHandler(new DefaultHttpRequestRetryHandler(maxRetries, true,
                        List.of(UnknownHostException.class, SSLException.class)) { })
                .setServiceUnavailableRetryStrategy(new ServerErrorRetryStrategy(maxRetries, retryInterval));

        String authToken = System.getProperty(HTTP_AUTH_TOKEN_PROPERTY);
        if (authToken != null) {
            builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
            });
        } else {
            builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                if (request instanceof HttpUriRequest && mavenSettings != null) {
                    String url = ((HttpRequestWrapper) request).getOriginal().getRequestLine().getUri();
                    Optional<Repository> matchingRepo = mavenSettings.getProfiles().stream()
                            .flatMap(profile -> profile.getRepositories().stream())
                            .filter(repository -> url.startsWith(repository.getUrl()))
                            .findFirst();
                    if (matchingRepo.isPresent()) {
                        Server server = mavenSettings.getServer(matchingRepo.get().getId());
                        if (server != null && server.getPassword() != null && !server.getPassword().isEmpty()) {
                            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + server.getPassword());
                        }
                    }
                }
            });
        }
        return builder.build();
    }
}
