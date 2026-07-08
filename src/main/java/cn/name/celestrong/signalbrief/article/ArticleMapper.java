package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文章写入与去重查询 Mapper。
 *
 * <p>写入路径和后续简报查询路径分离，避免在同一个 Mapper 中混杂不同读写模型。</p>
 */
@Mapper
public interface ArticleMapper {

    /**
     * 插入新文章；如命中数据库唯一索引则跳过。
     *
     * @return 实际插入行数，0 表示被唯一约束判定为重复
     */
    @Insert("""
            INSERT INTO article (
                source_name,
                source_url,
                category,
                title,
                url,
                guid,
                published_at,
                summary,
                content_text,
                content_hash
            ) VALUES (
                #{sourceName},
                #{sourceUrl},
                #{category},
                #{title},
                #{url},
                #{guid},
                #{publishedAt},
                #{summary},
                #{contentText},
                #{contentHash}
            )
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(NewArticle article);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE source_name = #{sourceName}
                  AND guid = #{guid}
            )
            """)
    boolean existsBySourceNameAndGuid(@Param("sourceName") String sourceName, @Param("guid") String guid);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE url = #{url}
            )
            """)
    boolean existsByUrl(@Param("url") String url);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE content_hash = #{contentHash}
            )
            """)
    boolean existsByContentHash(@Param("contentHash") String contentHash);

    /**
     * 兼容历史数据：只补齐空摘要或空正文，不覆盖已有内容。
     */
    @Update("""
            UPDATE article
            SET
                summary = CASE
                    WHEN (summary IS NULL OR btrim(summary) = '')
                         AND #{summary} IS NOT NULL
                         AND btrim(#{summary}) <> ''
                    THEN #{summary}
                    ELSE summary
                END,
                content_text = CASE
                    WHEN (content_text IS NULL OR btrim(content_text) = '')
                         AND #{contentText} IS NOT NULL
                         AND btrim(#{contentText}) <> ''
                    THEN #{contentText}
                    ELSE content_text
                END,
                updated_at = now()
            WHERE source_name = #{sourceName}
              AND guid = #{guid}
              AND (
                  ((summary IS NULL OR btrim(summary) = '')
                   AND #{summary} IS NOT NULL
                   AND btrim(#{summary}) <> '')
                  OR
                  ((content_text IS NULL OR btrim(content_text) = '')
                   AND #{contentText} IS NOT NULL
                   AND btrim(#{contentText}) <> '')
              )
            """)
    int fillMissingContentBySourceNameAndGuid(
            @Param("sourceName") String sourceName,
            @Param("guid") String guid,
            @Param("summary") String summary,
            @Param("contentText") String contentText
    );

    /**
     * 兼容历史数据：URL 唯一索引命中时补齐缺失内容。
     */
    @Update("""
            UPDATE article
            SET
                summary = CASE
                    WHEN (summary IS NULL OR btrim(summary) = '')
                         AND #{summary} IS NOT NULL
                         AND btrim(#{summary}) <> ''
                    THEN #{summary}
                    ELSE summary
                END,
                content_text = CASE
                    WHEN (content_text IS NULL OR btrim(content_text) = '')
                         AND #{contentText} IS NOT NULL
                         AND btrim(#{contentText}) <> ''
                    THEN #{contentText}
                    ELSE content_text
                END,
                updated_at = now()
            WHERE url = #{url}
              AND (
                  ((summary IS NULL OR btrim(summary) = '')
                   AND #{summary} IS NOT NULL
                   AND btrim(#{summary}) <> '')
                  OR
                  ((content_text IS NULL OR btrim(content_text) = '')
                   AND #{contentText} IS NOT NULL
                   AND btrim(#{contentText}) <> '')
              )
            """)
    int fillMissingContentByUrl(
            @Param("url") String url,
            @Param("summary") String summary,
            @Param("contentText") String contentText
    );

    /**
     * 兼容历史数据：缺少 guid 和 URL 时用内容哈希补齐缺失内容。
     */
    @Update("""
            UPDATE article
            SET
                summary = CASE
                    WHEN (summary IS NULL OR btrim(summary) = '')
                         AND #{summary} IS NOT NULL
                         AND btrim(#{summary}) <> ''
                    THEN #{summary}
                    ELSE summary
                END,
                content_text = CASE
                    WHEN (content_text IS NULL OR btrim(content_text) = '')
                         AND #{contentText} IS NOT NULL
                         AND btrim(#{contentText}) <> ''
                    THEN #{contentText}
                    ELSE content_text
                END,
                updated_at = now()
            WHERE content_hash = #{contentHash}
              AND (
                  ((summary IS NULL OR btrim(summary) = '')
                   AND #{summary} IS NOT NULL
                   AND btrim(#{summary}) <> '')
                  OR
                  ((content_text IS NULL OR btrim(content_text) = '')
                   AND #{contentText} IS NOT NULL
                   AND btrim(#{contentText}) <> '')
              )
            """)
    int fillMissingContentByContentHash(
            @Param("contentHash") String contentHash,
            @Param("summary") String summary,
            @Param("contentText") String contentText
    );
}
