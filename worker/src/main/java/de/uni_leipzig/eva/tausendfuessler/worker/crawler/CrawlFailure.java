package de.uni_leipzig.eva.tausendfuessler.worker.crawler;

public record CrawlFailure(String url, String error) implements CrawlOutcome {}
