package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用手写 Fake Mapper 验证入库编排，避免单元测试依赖数据库或 Mockito 运行时增强。
 */
class ArticleIngestionServiceTest {

    @Test
    void savesNewArticle() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "guid-1",
                "https://example.com/a",
                "Summary",
                null
        ));

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

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "guid-1",
                "https://example.com/a",
                "Summary",
                null
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, mapper.insertCalls);
    }

    @Test
    void fillsMissingContentWhenDuplicateArticleIsSeenAgain() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsBySourceNameAndGuid = true;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "guid-1",
                "https://example.com/a",
                "New summary",
                "New content text"
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, mapper.insertCalls);
        assertEquals(1, mapper.fillByGuidCalls);
        assertEquals("New summary", mapper.filledSummary);
        assertEquals("New content text", mapper.filledContentText);
    }

    @Test
    void fallsBackToUrlWhenGuidFillDoesNotChangeRows() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsBySourceNameAndGuid = true;
        mapper.fillByGuidResult = 0;
        mapper.fillByUrlResult = 1;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "new-guid",
                "https://example.com/a",
                "URL summary",
                "URL content text"
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, mapper.insertCalls);
        assertEquals(1, mapper.fillByGuidCalls);
        assertEquals(1, mapper.fillByUrlCalls);
        assertEquals("URL summary", mapper.filledSummary);
        assertEquals("URL content text", mapper.filledContentText);
    }

    @Test
    void fillsMissingContentWhenConcurrentInsertFindsDuplicate() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.insertResult = 0;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "guid-1",
                "https://example.com/a",
                "Concurrent summary",
                "Concurrent content text"
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(1, mapper.insertCalls);
        assertEquals(1, mapper.fillByGuidCalls);
        assertEquals("Concurrent summary", mapper.filledSummary);
        assertEquals("Concurrent content text", mapper.filledContentText);
    }

    @Test
    void fallsBackToUrlWhenConcurrentInsertConflictIsNotMatchedByGuid() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.insertResult = 0;
        mapper.fillByGuidResult = 0;
        mapper.fillByUrlResult = 1;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                "new-guid",
                "https://example.com/a",
                "Concurrent URL summary",
                "Concurrent URL content text"
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(1, mapper.insertCalls);
        assertEquals(1, mapper.fillByGuidCalls);
        assertEquals(1, mapper.fillByUrlCalls);
        assertEquals("Concurrent URL summary", mapper.filledSummary);
        assertEquals("Concurrent URL content text", mapper.filledContentText);
    }

    @Test
    void fallsBackToContentHashWhenConcurrentInsertConflictHasNoStableUrl() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.insertResult = 0;
        mapper.fillByHashResult = 1;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article(
                null,
                null,
                "Concurrent hash summary",
                "Concurrent hash content text"
        ));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(1, mapper.insertCalls);
        assertEquals(0, mapper.fillByGuidCalls);
        assertEquals(0, mapper.fillByUrlCalls);
        assertEquals(1, mapper.fillByHashCalls);
        assertEquals("Concurrent hash summary", mapper.filledSummary);
        assertEquals("Concurrent hash content text", mapper.filledContentText);
    }

    private FetchedArticle article(String guid, String url, String summary, String contentText) {
        return new FetchedArticle(
                "Test Source",
                URI.create("https://example.com/feed.xml"),
                ArticleCategory.JAVA,
                "Test Article",
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                summary,
                contentText
        );
    }

    /**
     * 只记录调用和返回预设结果，测试重点放在服务选择哪个 Mapper 方法。
     */
    private static final class FakeArticleMapper implements ArticleMapper {
        private boolean existsBySourceNameAndGuid;
        private boolean existsByUrl;
        private int insertResult = 1;
        private int fillByGuidResult = 1;
        private int fillByUrlResult;
        private int fillByHashResult;
        private int insertCalls;
        private int fillByGuidCalls;
        private int fillByUrlCalls;
        private int fillByHashCalls;
        private String filledSummary;
        private String filledContentText;

        @Override
        public int insertIfAbsent(NewArticle article) {
            insertCalls++;
            return insertResult;
        }

        @Override
        public boolean existsBySourceNameAndGuid(String sourceName, String guid) {
            return existsBySourceNameAndGuid;
        }

        @Override
        public boolean existsByUrl(String url) {
            return existsByUrl;
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            return false;
        }

        @Override
        public int fillMissingContentBySourceNameAndGuid(
                String sourceName,
                String guid,
                String summary,
                String contentText
        ) {
            fillByGuidCalls++;
            filledSummary = summary;
            filledContentText = contentText;
            return fillByGuidResult;
        }

        @Override
        public int fillMissingContentByUrl(String url, String summary, String contentText) {
            fillByUrlCalls++;
            filledSummary = summary;
            filledContentText = contentText;
            return fillByUrlResult;
        }

        @Override
        public int fillMissingContentByContentHash(String contentHash, String summary, String contentText) {
            fillByHashCalls++;
            filledSummary = summary;
            filledContentText = contentText;
            return fillByHashResult;
        }
    }
}
