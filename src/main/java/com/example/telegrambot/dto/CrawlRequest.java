package com.example.telegrambot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CrawlRequest {
    @JsonProperty("url")
    private String url;

    @JsonProperty("maxDepth")
    private int maxDepth;

    @JsonProperty("filters")
    private List<String> filters;

    public CrawlRequest() {}

    public CrawlRequest(String url, int maxDepth, List<String> filters) {
        this.url = url;
        this.maxDepth = maxDepth;
        this.filters = filters;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    public List<String> getFilters() { return filters; }
    public void setFilters(List<String> filters) { this.filters = filters; }
}
