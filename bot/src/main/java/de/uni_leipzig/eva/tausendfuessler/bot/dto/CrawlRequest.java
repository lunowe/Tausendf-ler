package de.uni_leipzig.eva.tausendfuessler.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** POST /api/jobs body. {@code owner} = Telegram chat id, used to scope /list. */
public class CrawlRequest {

    @JsonProperty("url")
    private String url;
    @JsonProperty("maxDepth")
    private int maxDepth;
    @JsonProperty("filters")
    private List<String> filters;
    @JsonProperty("owner")
    private long owner;

    public CrawlRequest() {}

    public CrawlRequest(String url, int maxDepth, List<String> filters, long owner) {
        this.url = url;
        this.maxDepth = maxDepth;
        this.filters = filters;
        this.owner = owner;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public List<String> getFilters() { return filters; }
    public void setFilters(List<String> filters) { this.filters = filters; }
    public long getOwner() { return owner; }
    public void setOwner(long owner) { this.owner = owner; }
}
