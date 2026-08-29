package de.uni_leipzig.eva.tausendfuessler.coordinator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret authentication for the REST API: every {@code /api/**} request must carry
 * {@code X-Api-Key: <key>} (property {@code tausendfuessler.api-key}, env {@code API_KEY}).
 * Exceptions: {@code GET /api/health} (monitoring) and {@code OPTIONS} (CORS preflight carries no custom headers).
 * If no key is configured the filter lets everything through, so local development stays zero-config.
 * Deliberately a plain servlet filter and no Spring Security: one header, one comparison.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    /** {@code null} = authentication disabled. */
    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${tausendfuessler.api-key:}") String apiKey) {
        this.apiKey = apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
        if (this.apiKey == null) {
            log.warn("API_KEY is not set - the REST API accepts every request without authentication");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return apiKey == null
                || !path.startsWith("/api/")
                || path.equals("/api/health")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        // constant-time comparison so response timing does not leak how many leading bytes matched
        if (presented != null && MessageDigest.isEqual(apiKey, presented.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("rejected {} {} from {}: {} header {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), HEADER, presented == null ? "missing" : "wrong");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
