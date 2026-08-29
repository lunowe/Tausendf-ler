package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

/** Lifecycle of a crawl job. Mirrors the bot's {@code JobStatus} enum (same names, see PROTOCOL.md). */
public enum JobStatus {
    PENDING, RUNNING, PAUSED, COMPLETED, ABORTED, FAILED
}
