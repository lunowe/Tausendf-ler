package com.example.telegrambot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PageResult {
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

    // Getter und Setter
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
