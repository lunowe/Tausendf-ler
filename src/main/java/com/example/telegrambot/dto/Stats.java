package com.example.telegrambot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Stats {
    @JsonProperty("totalJobs")
    private int totalJobs;

    @JsonProperty("totalPagesCrawled")
    private int totalPagesCrawled;

    @JsonProperty("topDomains")
    private Map<String, Integer> topDomains;

    @JsonProperty("activeJobs")
    private int activeJobs;

    public Stats() {}

    public int getTotalJobs() { return totalJobs; }
    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }

    public int getTotalPagesCrawled() { return totalPagesCrawled; }
    public void setTotalPagesCrawled(int totalPagesCrawled) { this.totalPagesCrawled = totalPagesCrawled; }

    public Map<String, Integer> getTopDomains() { return topDomains; }
    public void setTopDomains(Map<String, Integer> topDomains) { this.topDomains = topDomains; }

    public int getActiveJobs() { return activeJobs; }
    public void setActiveJobs(int activeJobs) { this.activeJobs = activeJobs; }
}
