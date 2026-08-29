package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Canonical form used for deduplication: strip fragment, trim trailing slash, lowercase scheme and host. */
public final class UrlNormalizer {

    private UrlNormalizer() {}

    /** @return normalized URL or {@code null} if the input is not an absolute http(s) URL */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            StringBuilder sb = new StringBuilder(scheme).append("://").append(host.toLowerCase(Locale.ROOT));
            if (uri.getPort() != -1) {
                sb.append(':').append(uri.getPort());
            }
            sb.append(path);
            if (uri.getRawQuery() != null) {
                sb.append('?').append(uri.getRawQuery());
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
