package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 文章去重服务。
 *
 * <p>去重顺序与数据库唯一索引保持一致：优先 guid，其次 URL，最后使用内容哈希兜底。</p>
 */
@Service
public class ArticleDeduplicationService {

    private final ArticleMapper articleMapper;

    public ArticleDeduplicationService(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    /**
     * 判断文章是否已经存在。
     *
     * <p>guid 只在同一来源内比较，URL 和内容哈希在全表范围内比较。</p>
     */
    public boolean exists(FetchedArticle article) {
        if (hasText(article.guid())) {
            return articleMapper.existsBySourceNameAndGuid(article.sourceName(), article.guid());
        }
        if (hasText(article.url())) {
            return articleMapper.existsByUrl(article.url());
        }
        return articleMapper.existsByContentHash(contentHash(article));
    }

    /**
     * 生成无 guid、无 URL 场景下的兜底去重键。
     *
     * <p>该算法会影响历史文章的重复判断，调整时必须同步评估数据库唯一索引和已有数据。</p>
     */
    public String contentHash(FetchedArticle article) {
        String source = normalize(article.title())
                + "|"
                + normalize(article.url())
                + "|"
                + normalize(article.sourceName())
                + "|"
                + normalize(article.publishedAt());
        return sha256(source);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalize(Instant value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
