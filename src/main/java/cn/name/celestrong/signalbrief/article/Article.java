package cn.name.celestrong.signalbrief.article;

import java.time.Instant;

/**
 * 已入库文章的读取模型。
 *
 * <p>字段与 {@code article} 表和 MyBatis 构造器映射保持一致，用于后续简报候选查询。</p>
 */
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
        String contentText,
        String contentHash,
        Instant createdAt,
        Instant updatedAt
) {
}
