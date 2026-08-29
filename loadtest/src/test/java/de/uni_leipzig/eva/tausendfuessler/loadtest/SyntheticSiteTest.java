package de.uni_leipzig.eva.tausendfuessler.loadtest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticSiteTest {

    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void servesDistinctPagesWithTitleMarkerAndText() throws Exception {
        try (SyntheticSite site = new SyntheticSite(new SyntheticSite.Config(20, 3, 2, 0, 1))) {
            HttpResponse<String> page7 = get(site.pageUrl(7));
            assertThat(page7.statusCode()).isEqualTo(200);
            assertThat(page7.body()).contains("<title>Seite 7</title>").contains("markerwort00007");
            assertThat(page7.body().length()).isGreaterThan(1_000);

            assertThat(get(site.startUrl()).body()).contains("<title>Seite 0</title>").doesNotContain("markerwort00007");
            assertThat(get(site.pageUrl(20)).statusCode()).isEqualTo(404);
            assertThat(get(site.baseUrl() + "/nope").statusCode()).isEqualTo(404);
        }
    }

    @Test
    void everyLinkResolvesAndPopularPagesAreLinkedFromEveryPage() throws Exception {
        try (SyntheticSite site = new SyntheticSite(new SyntheticSite.Config(30, 4, 2, 0, 42))) {
            for (int i = 0; i < site.config().pages(); i++) {
                List<String> hrefs = hrefs(get(site.pageUrl(i)).body());
                assertThat(hrefs).as("page %d", i).hasSize(site.linksOf(i).size()).doesNotContain("/p/" + i);
                for (String href : hrefs) {
                    assertThat(get(site.baseUrl() + href).statusCode()).as("link %s on page %d", href, i).isEqualTo(200);
                }
                for (int popular = 0; popular < 2; popular++) {
                    if (popular != i) {
                        assertThat(hrefs).contains("/p/" + popular);
                    }
                }
            }
        }
    }

    @Test
    void linksAreDeterministicPerSeed() {
        SyntheticSite.Config config = new SyntheticSite.Config(100, 5, 0, 0, 7);
        assertThat(linksWith(config)).isEqualTo(linksWith(config))
                .isNotEqualTo(linksWith(new SyntheticSite.Config(100, 5, 0, 0, 8)));
    }

    @Test
    void honoursDelay() throws Exception {
        try (SyntheticSite site = new SyntheticSite(new SyntheticSite.Config(5, 1, 0, 150, 1))) {
            long start = System.nanoTime();
            assertThat(get(site.startUrl()).statusCode()).isEqualTo(200);
            assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(150);
        }
    }

    private static List<List<Integer>> linksWith(SyntheticSite.Config config) {
        try (SyntheticSite site = new SyntheticSite(config)) {
            List<List<Integer>> links = new ArrayList<>();
            for (int i = 0; i < config.pages(); i++) {
                links.add(site.linksOf(i));
            }
            return links;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> hrefs(String html) {
        List<String> hrefs = new ArrayList<>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            hrefs.add(matcher.group(1));
        }
        return hrefs;
    }
}
