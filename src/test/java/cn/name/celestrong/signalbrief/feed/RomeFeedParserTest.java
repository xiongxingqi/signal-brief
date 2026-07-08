package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.content.HtmlContentCleaner;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RSS fixture 覆盖 RSS 2.0 和 Atom 两类格式，防止解析器只适配单一 feed 结构。
 */
class RomeFeedParserTest {

    private final RomeFeedParser parser = new RomeFeedParser(
            new FeedEntryContentExtractor(new HtmlContentCleaner())
    );

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

    @Test
    void parsesAtomSummaryAndContentSeparately() throws Exception {
        FeedProperties.FeedSource source = source("Atom Feed");

        try (InputStream inputStream = fixture("fixtures/rss/atom-content.xml")) {
            FetchedArticle article = parser.parse(source, inputStream).getFirst();

            assertEquals("Short atom summary.", article.summary());
            assertEquals("Full atom content body.", article.contentText());
        }
    }

    @Test
    void parsesRssContentEncodedAsContentText() throws Exception {
        FeedProperties.FeedSource source = source("RSS Feed");

        try (InputStream inputStream = fixture("fixtures/rss/rss-content-encoded.xml")) {
            FetchedArticle article = parser.parse(source, inputStream).getFirst();

            assertEquals("Short rss description.", article.summary());
            assertEquals("Full rss content body.", article.contentText());
        }
    }

    @Test
    void usesContentAsSummaryFallbackWhenDescriptionIsMissing() throws Exception {
        FeedProperties.FeedSource source = source("HTML Feed");

        try (InputStream inputStream = fixture("fixtures/rss/html-content.xml")) {
            FetchedArticle article = parser.parse(source, inputStream).getFirst();

            assertEquals("Only content fallback.", article.summary());
            assertEquals("Only content fallback.", article.contentText());
        }
    }

    private FeedProperties.FeedSource source(String name) {
        return new FeedProperties.FeedSource(
                name,
                URI.create("https://example.com/feed.xml"),
                ArticleCategory.JAVA,
                true
        );
    }

    private InputStream fixture(String path) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }
        return inputStream;
    }
}
