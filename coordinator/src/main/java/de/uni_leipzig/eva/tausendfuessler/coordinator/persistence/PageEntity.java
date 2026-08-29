package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pages", indexes = @Index(name = "idx_pages_job_seq", columnList = "jobId, seq"))
public class PageEntity {

    public static final int SNIPPET_MAX = 300;
    public static final int TITLE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String jobId;

    /** Per-job monotonic sequence number; the bot's poll cursor. */
    private long seq;

    @Column(nullable = false, length = 2048)
    private String url;

    private int depth;
    private int httpStatus;

    @Column(length = TITLE_MAX)
    private String title;

    @Column(length = SNIPPET_MAX)
    private String textSnippet;

    private Instant crawledAt;

    @Column(length = 1000)
    private String error;

    protected PageEntity() {}

    public PageEntity(String jobId, long seq, String url, int depth, int httpStatus,
                      String title, String textSnippet, Instant crawledAt, String error) {
        this.jobId = jobId;
        this.seq = seq;
        this.url = url;
        this.depth = depth;
        this.httpStatus = httpStatus;
        this.title = truncate(title, TITLE_MAX);
        this.textSnippet = truncate(textSnippet, SNIPPET_MAX);
        this.crawledAt = crawledAt;
        this.error = truncate(error, 1000);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public long getSeq() { return seq; }
    public String getUrl() { return url; }
    public int getDepth() { return depth; }
    public int getHttpStatus() { return httpStatus; }
    public String getTitle() { return title; }
    public String getTextSnippet() { return textSnippet; }
    public Instant getCrawledAt() { return crawledAt; }
    public String getError() { return error; }
}
