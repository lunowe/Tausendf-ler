package de.uni_leipzig.eva.tausendfuessler.bot.dto;

import java.time.Instant;

/** One connected worker as reported by {@code GET /api/workers}. */
public record WorkerInfo(String workerId, int threads, int inFlight, Instant connectedAt) {
}
