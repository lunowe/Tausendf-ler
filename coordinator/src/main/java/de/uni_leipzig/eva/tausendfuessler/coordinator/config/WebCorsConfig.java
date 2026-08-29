package de.uni_leipzig.eva.tausendfuessler.coordinator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser frontend (Next.js, default {@code http://localhost:3000}) call the REST API from
 * JavaScript. Origins come from {@code tausendfuessler.cors-origins} (comma-separated). The Telegram
 * bot talks to the coordinator server-side and is not affected by CORS.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfig(@Value("${tausendfuessler.cors-origins:http://localhost:3000}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins.clone();
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", ApiKeyFilter.HEADER)
                .maxAge(3600);
    }
}
