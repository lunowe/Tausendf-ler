package de.uni_leipzig.eva.krakensuche.worker.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

public final class HtmlExtractor {

    public Extraction extract(String url, String html) {
        var doc = Jsoup.parse(html, url);
        var title = extractTitle(doc);
        var plainText = extractText(doc);
        var links = extractLinks(doc, url);
        return new Extraction(title, plainText, links);
    }

    private String extractTitle(Document doc) {
        var title = doc.title();
        return title.isBlank() ? null : title.strip();
    }

    private String extractText(Document doc) {
        var text = doc.body() != null ? doc.body().text() : "";
        return text.isBlank() ? "" : text.strip();
    }

    private List<String> extractLinks(Document doc, String baseUrl) {
        return doc.select("a[href]").stream()
                .map(e -> e.absUrl("href"))
                .filter(href -> !href.isBlank())
                .distinct()
                .flatMap(this::filterHttp)
                .toList();
    }

    private Stream<String> filterHttp(String url) {
        try {
            var uri = URI.create(url);
            var scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return Stream.of(url);
            }
        } catch (Exception ignored) {
        }
        return Stream.empty();
    }

    public record Extraction(String title, String plainText, List<String> outgoingLinks) {}
}
