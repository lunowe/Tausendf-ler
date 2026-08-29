package de.uni_leipzig.eva.tausendfuessler.bot.service;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/** Adds the coordinator's API key as {@code X-Api-Key} header to every request. */
public class ApiKeyInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Api-Key";

    private final String apiKey;

    public ApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(HEADER, apiKey);
        return execution.execute(request, body);
    }
}
