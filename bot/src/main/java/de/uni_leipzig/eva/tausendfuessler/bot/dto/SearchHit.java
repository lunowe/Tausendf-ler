package de.uni_leipzig.eva.tausendfuessler.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One hit of GET /api/search. */
public class SearchHit {

    @JsonProperty("url")
    private String url;
    @JsonProperty("title")
    private String title;
    @JsonProperty("textSnippet")
    private String textSnippet;
    @JsonProperty("jobId")
    private String jobId;

    public SearchHit() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTextSnippet() { return textSnippet; }
    public void setTextSnippet(String textSnippet) { this.textSnippet = textSnippet; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
}
