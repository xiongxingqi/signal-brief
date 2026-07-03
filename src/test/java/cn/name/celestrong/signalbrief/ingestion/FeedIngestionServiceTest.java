package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedIngestionServiceTest {

    @Test
    void ingestsEnabledFeedsAndAggregatesStatistics() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Disabled", URI.create("https://example.com/disabled.xml"), ArticleCategory.JAVA, false)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService
        );

        FeedIngestionResult result = service.ingestEnabledFeeds();

        assertEquals(1, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    @Test
    void continuesWhenOneSourceFails() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Failing", URI.create("https://example.com/failing.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService
        );

        FeedIngestionResult result = service.ingestEnabledFeeds();

        assertEquals(2, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    private static final class FakeFeedClient implements FeedClient {
        @Override
        public InputStream fetch(FeedProperties.FeedSource source) {
            if ("Failing".equals(source.name())) {
                throw new IllegalStateException("feed unavailable");
            }
            return new ByteArrayInputStream("<feed/>".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FakeFeedParser implements FeedParser {
        @Override
        public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
            return List.of(
                    article(source, "guid-1"),
                    article(source, "guid-2")
            );
        }

        private FetchedArticle article(FeedProperties.FeedSource source, String guid) {
            return new FetchedArticle(
                    source.name(),
                    source.url(),
                    source.category(),
                    "Title " + guid,
                    "https://example.com/" + guid,
                    guid,
                    Instant.parse("2026-07-03T00:00:00Z"),
                    "Summary"
            );
        }
    }

    private static final class FakeArticleIngestionService extends ArticleIngestionService {
        private int calls;

        private FakeArticleIngestionService() {
            super(null, null);
        }

        @Override
        public Result ingest(FetchedArticle article) {
            calls++;
            return "guid-1".equals(article.guid()) ? new Result(1, 0) : new Result(0, 1);
        }
    }
}
