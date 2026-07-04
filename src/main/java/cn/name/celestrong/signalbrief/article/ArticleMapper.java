package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
