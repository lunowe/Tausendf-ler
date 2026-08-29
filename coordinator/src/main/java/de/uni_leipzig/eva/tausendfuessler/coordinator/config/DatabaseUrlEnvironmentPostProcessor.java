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
 * Accepts {@code DB_URL} or {@code DATABASE_URL} in the form {@code postgres://user:password@host:port/db} (what
 * Railway, Heroku and friends provide) and translates it into the JDBC datasource properties. A {@code jdbc:} URL is
 * passed through untouched, so local setups are unaffected. Registered in {@code META-INF/spring.factories}.
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        // DB_URL wins if set; either variable may carry the postgres:// form and is then translated
        String candidate = firstNonBlank(env.getProperty("DB_URL"), env.getProperty("DATABASE_URL"));
        if (candidate == null || candidate.trim().startsWith("jdbc:")) {
            return;
        }
        Map<String, Object> props = toDataSourceProperties(candidate);
        if (!props.isEmpty()) {
            // explicit DB_USER/DB_PASSWORD still override credentials embedded in the URL
            if (!isBlank(env.getProperty("DB_USER"))) {
                props.remove("spring.datasource.username");
            }
            if (!isBlank(env.getProperty("DB_PASSWORD"))) {
                props.remove("spring.datasource.password");
            }
            env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
        }
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : (!isBlank(b) ? b : null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
