package org.wildfly.prospero.extras.http;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import java.util.Optional;

public class AuthenticatingHttpClientBuilder {

    private static final String HTTP_AUTH_TOKEN_PROPERTY = "prospero.extras.http.auth.token";

    public static CloseableHttpClient build(Settings mavenSettings) {
        HttpClientBuilder builder = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy());

        String authToken = System.getProperty(HTTP_AUTH_TOKEN_PROPERTY);
        if (authToken != null) {
            builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
            });
        } else {
            builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                if (request instanceof HttpUriRequest) {
                    String url = ((HttpUriRequest) request).getURI().toURL().toExternalForm();
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
