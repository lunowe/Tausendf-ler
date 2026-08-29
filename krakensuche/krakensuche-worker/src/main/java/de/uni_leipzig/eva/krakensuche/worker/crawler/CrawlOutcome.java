package de.uni_leipzig.eva.krakensuche.worker.crawler;

public sealed interface CrawlOutcome permits CrawlSuccess, CrawlFailure {

    String url();
}
