package cn.name.celestrong.signalbrief.article;

import java.time.Instant;

public record NewArticle(
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary,
        String contentHash
) {
    public NewArticle {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Article sourceName must not be blank");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Article sourceUrl must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Article category must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Article title must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("Article contentHash must not be blank");
        }
    }
}
