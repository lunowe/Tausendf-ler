package de.uni_leipzig.eva.tausendfuessler.worker.crawler;

public sealed interface CrawlOutcome permits CrawlSuccess, CrawlFailure {

    String url();
}
