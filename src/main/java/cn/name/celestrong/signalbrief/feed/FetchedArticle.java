package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;

import java.net.URI;
import java.time.Instant;

/**
 * 从 RSS / Atom 中解析出的未落库文章。
 *
 * <p>该模型保留外部源上下文，供后续去重和入库使用；文章 URL、guid 和发布时间允许缺失。</p>
 */
public record FetchedArticle(
        String sourceName,
        URI sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary,
        String contentText
) {
}
