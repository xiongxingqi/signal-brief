package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;

import java.net.URI;
import java.time.Instant;

public record FetchedArticle(
        String sourceName,
        URI sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary
) {
}
