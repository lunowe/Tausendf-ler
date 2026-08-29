package de.uni_leipzig.eva.tausendfuessler.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One crawled page as delivered by GET /api/jobs/{id}/results. {@code seq} is the cursor for the next poll. */
public class PageResult {

    @JsonProperty("seq")
    private long seq;
    @JsonProperty("url")
    private String url;
    @JsonProperty("title")
    private String title;
    @JsonProperty("textSnippet")
    private String textSnippet;
    @JsonProperty("depth")
    private int depth;
    @JsonProperty("crawledAt")
    private String crawledAt;

    public PageResult() {}

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTextSnippet() { return textSnippet; }
    public void setTextSnippet(String textSnippet) { this.textSnippet = textSnippet; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getCrawledAt() { return crawledAt; }
    public void setCrawledAt(String crawledAt) { this.crawledAt = crawledAt; }
}
