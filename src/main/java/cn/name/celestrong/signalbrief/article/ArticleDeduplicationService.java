package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class ArticleDeduplicationService {

    private final ArticleMapper articleMapper;

    public ArticleDeduplicationService(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    public boolean exists(FetchedArticle article) {
        if (hasText(article.guid())) {
            return articleMapper.existsBySourceNameAndGuid(article.sourceName(), article.guid());
        }
        if (hasText(article.url())) {
            return articleMapper.existsByUrl(article.url());
        }
        return articleMapper.existsByContentHash(contentHash(article));
    }

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
