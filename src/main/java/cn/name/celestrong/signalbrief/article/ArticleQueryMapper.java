package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 简报候选文章查询 Mapper。
 *
 * <p>该 Mapper 使用构造器映射，SQL 列别名需要与 {@code Article} record 构造参数保持同步。</p>
 */
@Mapper
public interface ArticleQueryMapper {

    /**
     * 查询指定时间窗口内可进入简报的文章。
     *
     * <p>时间窗口使用半开区间，文章有效时间优先取 published_at，缺失时回退 created_at。</p>
     */
    @Select("""
            SELECT
                id,
                source_name AS "sourceName",
                source_url AS "sourceUrl",
                category,
                title,
                url,
                guid,
                published_at AS "publishedAt",
                summary,
                content_text AS "contentText",
                content_hash AS "contentHash",
                created_at AS "createdAt",
                updated_at AS "updatedAt"
            FROM article
            WHERE COALESCE(published_at, created_at) >= #{startInclusive}
              AND COALESCE(published_at, created_at) < #{endExclusive}
            ORDER BY category ASC, COALESCE(published_at, created_at) DESC, id DESC
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sourceName", javaType = String.class),
            @Arg(column = "sourceUrl", javaType = String.class),
            @Arg(column = "category", javaType = ArticleCategory.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "url", javaType = String.class),
            @Arg(column = "guid", javaType = String.class),
            @Arg(column = "publishedAt", javaType = Instant.class),
            @Arg(column = "summary", javaType = String.class),
            @Arg(column = "contentText", javaType = String.class),
            @Arg(column = "contentHash", javaType = String.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class)
    })
    List<Article> findBriefCandidates(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );
}
