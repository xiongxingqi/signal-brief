package cn.name.celestrong.signalbrief.article;

import java.time.Instant;

public record Article(
        Long id,
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary,
        String contentHash,
        Instant createdAt,
        Instant updatedAt
) {
}
