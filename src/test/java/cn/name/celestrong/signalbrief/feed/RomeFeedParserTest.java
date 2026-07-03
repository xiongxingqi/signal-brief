package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RomeFeedParserTest {

    private final RomeFeedParser parser = new RomeFeedParser();

    @Test
    void parsesRssItem() throws Exception {
        FeedProperties.FeedSource source = new FeedProperties.FeedSource(
                "Spring Blog",
                URI.create("https://spring.io/blog.atom"),
                ArticleCategory.FRAMEWORK,
                true
        );

        try (InputStream inputStream = fixture("fixtures/rss/spring-blog.xml")) {
            List<FetchedArticle> articles = parser.parse(source, inputStream);

            assertEquals(1, articles.size());
            FetchedArticle article = articles.getFirst();
            assertEquals("Spring Blog", article.sourceName());
            assertEquals(ArticleCategory.FRAMEWORK, article.category());
            assertEquals("Spring Boot 4.0.7 available now", article.title());
            assertEquals("https://spring.io/blog/2026/07/01/spring-boot-4-0-7-available-now", article.url());
            assertEquals("spring-boot-4.0.7", article.guid());
            assertEquals(Instant.parse("2026-07-01T10:00:00Z"), article.publishedAt());
            assertEquals("Spring Boot 4.0.7 has been released.", article.summary());
        }
    }

    @Test
    void parsesAtomEntryUpdatedDateWhenPublishedDateIsMissing() throws Exception {
        FeedProperties.FeedSource source = new FeedProperties.FeedSource(
                "Inside Java",
                URI.create("https://inside.java/feed.xml"),
                ArticleCategory.JAVA,
                true
        );

        try (InputStream inputStream = fixture("fixtures/rss/inside-java.atom")) {
            List<FetchedArticle> articles = parser.parse(source, inputStream);

            assertEquals(1, articles.size());
            FetchedArticle article = articles.getFirst();
            assertEquals("inside-java-jdk-update-notes", article.guid());
            assertEquals("https://inside.java/2026/07/02/jdk-update-notes", article.url());
            assertEquals(Instant.parse("2026-07-02T08:30:00Z"), article.publishedAt());
            assertNotNull(article.summary());
        }
    }

    private InputStream fixture(String path) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }
        return inputStream;
    }
}
