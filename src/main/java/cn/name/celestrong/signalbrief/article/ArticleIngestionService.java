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
        String contentHash = deduplicationService.contentHash(article);
        boolean duplicate = deduplicationService.exists(article);
        if (duplicate) {
            fillMissingContentForDuplicate(article, contentHash);
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
                article.contentText(),
                contentHash
        );
        // 即使应用层判断未重复，并发批次仍可能触发数据库唯一索引冲突。
        int insertedRows = articleMapper.insertIfAbsent(newArticle);
        if (insertedRows == 1) {
            return new Result(1, 0);
        }
        fillMissingContentForDuplicate(article, contentHash);
        return new Result(0, 1);
    }

    private void fillMissingContentForDuplicate(FetchedArticle article, String contentHash) {
        // TODO(COMPATIBILITY): 历史文章可能没有 contentText；线上稳定并完成数据补齐后再移除该降级路径。
        if (hasText(article.guid())) {
            int updatedRows = articleMapper.fillMissingContentBySourceNameAndGuid(
                    article.sourceName(),
                    article.guid(),
                    article.summary(),
                    article.contentText()
            );
            if (updatedRows > 0) {
                return;
            }
        }
        if (hasText(article.url())) {
            int updatedRows = articleMapper.fillMissingContentByUrl(article.url(), article.summary(), article.contentText());
            if (updatedRows > 0) {
                return;
            }
        }
        articleMapper.fillMissingContentByContentHash(contentHash, article.summary(), article.contentText());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 单篇文章入库结果，供上层批次统计累加。
     */
    public record Result(int insertedCount, int skippedCount) {
    }
}
