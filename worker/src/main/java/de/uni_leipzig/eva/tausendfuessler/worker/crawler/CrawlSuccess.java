package de.uni_leipzig.eva.tausendfuessler.worker.crawler;

import java.util.List;

public record CrawlSuccess(String url, int httpStatus, String title, String plainText, List<String> outgoingLinks) implements CrawlOutcome {}
