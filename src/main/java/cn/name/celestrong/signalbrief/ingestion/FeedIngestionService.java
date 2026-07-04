package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * RSS 入库批次编排服务。
 *
 * <p>单个源失败只影响当前源，批次继续处理后续源，并在结果中累计失败源数量。</p>
 */
@Service
public class FeedIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionService.class);

    private final FeedProperties feedProperties;
    private final FeedClient feedClient;
    private final FeedParser feedParser;
    private final ArticleIngestionService articleIngestionService;

    public FeedIngestionService(
            FeedProperties feedProperties,
            FeedClient feedClient,
            FeedParser feedParser,
            ArticleIngestionService articleIngestionService
    ) {
        this.feedProperties = feedProperties;
        this.feedClient = feedClient;
        this.feedParser = feedParser;
        this.articleIngestionService = articleIngestionService;
    }

    public FeedIngestionResult ingestEnabledFeeds() {
        FeedIngestionResult result = FeedIngestionResult.empty();
        for (FeedProperties.FeedSource source : feedProperties.enabledFeeds()) {
            result = result.plus(ingestSource(source));
        }
        log.info(
                "Feed ingestion completed: sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
        return result;
    }

    private FeedIngestionResult ingestSource(FeedProperties.FeedSource source) {
        // FeedClient 契约要求调用方关闭输入流，避免远程响应或临时资源泄露。
        try (InputStream inputStream = feedClient.fetch(source)) {
            List<FetchedArticle> articles = feedParser.parse(source, inputStream);
            int insertedCount = 0;
            int skippedCount = 0;
            for (FetchedArticle article : articles) {
                ArticleIngestionService.Result result = articleIngestionService.ingest(article);
                insertedCount += result.insertedCount();
                skippedCount += result.skippedCount();
            }
            return new FeedIngestionResult(1, articles.size(), insertedCount, skippedCount, 0);
        } catch (Exception ex) {
            log.warn("Failed to ingest feed source name={}, url={}", source.name(), source.url(), ex);
            return new FeedIngestionResult(1, 0, 0, 0, 1);
        }
    }
}
