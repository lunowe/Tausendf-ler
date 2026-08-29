package de.uni_leipzig.eva.tausendfuessler.coordinator.socket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The shared secret workers must present in {@code REGISTER.token} ({@code tausendfuessler.worker-token},
 * env {@code WORKER_TOKEN}). Not configured = every worker is accepted.
 */
final class WorkerToken {

    /** {@code null} = check disabled. */
    private final byte[] expected;

    WorkerToken(String configured) {
        this.expected = configured == null || configured.isBlank() ? null : configured.getBytes(StandardCharsets.UTF_8);
    }

    boolean enabled() {
        return expected != null;
    }

    boolean matches(String presented) {
        if (expected == null) {
            return true;
        }
        return presented != null && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}
