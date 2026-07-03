package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

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
        int insertedRows = articleMapper.insertIfAbsent(newArticle);
        return insertedRows == 1 ? new Result(1, 0) : new Result(0, 1);
    }

    public record Result(int insertedCount, int skippedCount) {
    }
}
