package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleIngestionServiceTest {

    @Test
    void savesNewArticle() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article("guid-1", "https://example.com/a"));

        assertEquals(1, result.insertedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(1, mapper.insertCalls);
    }

    @Test
    void skipsDuplicateArticle() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsBySourceNameAndGuid = true;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article("guid-1", "https://example.com/a"));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, mapper.insertCalls);
    }

    private FetchedArticle article(String guid, String url) {
        return new FetchedArticle(
                "Test Source",
                URI.create("https://example.com/feed.xml"),
                ArticleCategory.JAVA,
                "Test Article",
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                "Summary"
        );
    }

    private static final class FakeArticleMapper implements ArticleMapper {
        private boolean existsBySourceNameAndGuid;
        private int insertCalls;

        @Override
        public int insertIfAbsent(NewArticle article) {
            insertCalls++;
            return 1;
        }

        @Override
        public boolean existsBySourceNameAndGuid(String sourceName, String guid) {
            return existsBySourceNameAndGuid;
        }

        @Override
        public boolean existsByUrl(String url) {
            return false;
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            return false;
        }
    }
}
