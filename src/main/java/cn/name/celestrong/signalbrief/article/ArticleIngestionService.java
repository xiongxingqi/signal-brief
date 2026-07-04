package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

/**
 * 单篇文章入库服务。
 *
 * <p>应用层先做可读的去重判断，数据库唯一索引继续作为并发和竞态场景下的最终兜底。</p>
 */
@Service
public class ArticleIngestionService {

    private final ArticleMapper articleMapper;
    private final ArticleDeduplicationService deduplicationService;

    public ArticleIngestionService(
            ArticleMapper articleMapper,
            ArticleDeduplicationService deduplicationService
    ) {
        this.articleMapper = articleMapper;
        this.deduplicationService = deduplicationService;
    }

    public Result ingest(FetchedArticle article) {
        if (deduplicationService.exists(article)) {
            return new Result(0, 1);
        }

        NewArticle newArticle = new NewArticle(
                article.sourceName(),
                article.sourceUrl().toString(),
                article.category(),
                article.title(),
                article.url(),
                article.guid(),
                article.publishedAt(),
                article.summary(),
                deduplicationService.contentHash(article)
        );
        // 即使应用层判断未重复，并发批次仍可能触发数据库唯一索引冲突。
        int insertedRows = articleMapper.insertIfAbsent(newArticle);
        return insertedRows == 1 ? new Result(1, 0) : new Result(0, 1);
    }

    /**
     * 单篇文章入库结果，供上层批次统计累加。
     */
    public record Result(int insertedCount, int skippedCount) {
    }
}
