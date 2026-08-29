package de.uni_leipzig.eva.krakensuche.worker.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlExtractorTest {

    private final HtmlExtractor extractor = new HtmlExtractor();

    @Test
    void extractsTitleAndTextAndLinks() {
        var html = """
                <html><head><title>Test Page</title></head>
                <body>
                    <p>Hello world</p>
                    <a href="https://example.com/page1">link1</a>
                    <a href="/relative">relative</a>
                    <a href="ftp://files.example.com">skip me</a>
                </body></html>
                """;

        var result = extractor.extract("https://example.com", html);

        assertThat(result.title()).isEqualTo("Test Page");
        assertThat(result.plainText()).contains("Hello world");
        assertThat(result.outgoingLinks())
                .contains("https://example.com/page1")
                .contains("https://example.com/relative")
                .doesNotContain("ftp://files.example.com");
    }

    @Test
    void missingTitleReturnsNull() {
        var html = "<html><body><p>no title</p></body></html>";
        var result = extractor.extract("https://example.com", html);
        assertThat(result.title()).isNull();
    }

    @Test
    void emptyTitleReturnsNull() {
        var html = "<html><head><title></title></head><body><p>hi</p></body></html>";
        var result = extractor.extract("https://example.com", html);
        assertThat(result.title()).isNull();
    }

    @Test
    void noBodyReturnsEmptyText() {
        var html = "<html></html>";
        var result = extractor.extract("https://example.com", html);
        assertThat(result.plainText()).isEmpty();
    }

    @Test
    void noLinksReturnsEmptyList() {
        var html = "<html><body><p>no links</p></body></html>";
        var result = extractor.extract("https://example.com", html);
        assertThat(result.outgoingLinks()).isEmpty();
    }

    @Test
    void resolvesRelativeLinks() {
        var html = """
                <html><body>
                    <a href="/path/to/page">rel</a>
                    <a href="other">rel2</a>
                </body></html>
                """;

        var result = extractor.extract("https://example.com/base/", html);

        assertThat(result.outgoingLinks())
                .contains("https://example.com/path/to/page")
                .contains("https://example.com/base/other");
    }

    @Test
    void filtersNonHttpLinks() {
        var html = """
                <html><body>
                    <a href="https://valid.com">valid</a>
                    <a href="mailto:test@example.com">mail</a>
                    <a href="javascript:void(0)">js</a>
                    <a href="ftp://files.example.com">ftp</a>
                </body></html>
                """;

        var result = extractor.extract("https://example.com", html);

        assertThat(result.outgoingLinks()).containsExactly("https://valid.com");
    }

    @Test
    void deduplicatesLinks() {
        var html = """
                <html><body>
                    <a href="https://example.com/page">first</a>
                    <a href="https://example.com/page">second</a>
                </body></html>
                """;

        var result = extractor.extract("https://example.com", html);

        assertThat(result.outgoingLinks()).hasSize(1);
    }

    @Test
    void handlesMalformedHtml() {
        var html = "<html><body><p>unclosed<p>another" + "</body>";
        var result = extractor.extract("https://example.com", html);
        assertThat(result.plainText()).isNotBlank();
    }
}
