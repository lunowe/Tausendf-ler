package de.uni_leipzig.eva.tausendfuessler.coordinator.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts a {@code DATABASE_URL} in the form {@code postgres://user:password@host:port/db} (what Railway, Heroku
 * and friends provide) and translates it into the JDBC datasource properties. {@code DB_URL} (JDBC form) still wins
 * if it is set, so local setups are unaffected. Registered in {@code META-INF/spring.factories}.
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String jdbcUrl = env.getProperty("DB_URL");
        String databaseUrl = env.getProperty("DATABASE_URL");
        if ((jdbcUrl != null && !jdbcUrl.isBlank()) || databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        Map<String, Object> props = toDataSourceProperties(databaseUrl);
        if (!props.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
        }
    }

    /** {@code postgres://u:p@h:5432/db?sslmode=require} → spring.datasource.url/username/password; empty map if unparseable. */
    static Map<String, Object> toDataSourceProperties(String databaseUrl) {
        Map<String, Object> props = new LinkedHashMap<>();
        try {
            URI uri = URI.create(databaseUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("postgres") || scheme.equals("postgresql")) || uri.getHost() == null) {
                return props;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
                props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
                if (colon >= 0) {
                    props.put("spring.datasource.password", URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
        return props;
    }
}
