package de.uni_leipzig.eva.tausendfuessler.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Line-delimited JSON protocol between coordinator and worker.
 * Every line on the socket is exactly one serialized {@code Message}; the {@code type} field selects the subtype.
 * See PROTOCOL.md for the message flow.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Message.Register.class, name = "REGISTER"),
        @JsonSubTypes.Type(value = Message.Registered.class, name = "REGISTERED"),
        @JsonSubTypes.Type(value = Message.RequestWork.class, name = "REQUEST_WORK"),
        @JsonSubTypes.Type(value = Message.WorkPackage.class, name = "WORK_PACKAGE"),
        @JsonSubTypes.Type(value = Message.NoWork.class, name = "NO_WORK"),
        @JsonSubTypes.Type(value = Message.PageResult.class, name = "PAGE_RESULT"),
        @JsonSubTypes.Type(value = Message.JobSignal.class, name = "JOB_SIGNAL"),
        @JsonSubTypes.Type(value = Message.Error.class, name = "ERROR"),
})
public sealed interface Message {

    // ---- worker -> coordinator ----

    /**
     * First message after connect. {@code threads} = size of the worker's crawl thread pool.
     * {@code token} = shared secret ({@code WORKER_TOKEN}); may be {@code null} if the coordinator runs without one.
     */
    record Register(String workerId, int threads, String token) implements Message {}

    /** Worker asks for the next URL package; {@code capacity} = free slots in its pool. */
    record RequestWork(String workerId, int capacity) implements Message {}

    /** One crawled page. {@code error == null} means success. */
    record PageResult(
            String workerId,
            String jobId,
            String url,
            int depth,
            int httpStatus,
            String title,
            String textSnippet,
            List<String> links,
            String error,
            long crawledAtEpochMs
    ) implements Message {}

    // ---- coordinator -> worker ----

    record Registered(String workerId) implements Message {}

    /** A batch of URLs of one job at one depth. */
    record WorkPackage(String jobId, int depth, List<String> urls, List<String> filters) implements Message {}

    /** Nothing to do right now; worker should wait a moment before asking again. */
    record NoWork(long retryAfterMs) implements Message {}

    /** Pushed control signal for a job. */
    record JobSignal(String jobId, Signal signal) implements Message {}

    enum Signal { PAUSE, RESUME, ABORT }

    // ---- either direction ----

    record Error(String message) implements Message {}
}
