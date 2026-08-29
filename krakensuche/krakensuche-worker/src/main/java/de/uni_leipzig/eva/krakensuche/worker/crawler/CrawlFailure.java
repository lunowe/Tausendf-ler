package de.uni_leipzig.eva.krakensuche.worker.crawler;

public record CrawlFailure(String url, String error) implements CrawlOutcome {}
