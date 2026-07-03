package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleMapper {

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
