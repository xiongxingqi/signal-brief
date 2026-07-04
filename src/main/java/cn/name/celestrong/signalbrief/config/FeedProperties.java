package cn.name.celestrong.signalbrief.config;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

/**
 * RSS 源配置根对象。
 *
 * <p>配置在绑定后复制为不可变列表，避免批次执行过程中被外部修改影响。</p>
 */
@ConfigurationProperties(prefix = "signal-brief")
public record FeedProperties(List<FeedSource> feeds) {

    public FeedProperties {
        feeds = feeds == null ? List.of() : List.copyOf(feeds);
    }

    public List<FeedSource> enabledFeeds() {
        return feeds.stream()
                .filter(FeedSource::enabled)
                .toList();
    }

    /**
     * 单个 RSS / Atom 源的配置项。
     *
     * <p>源名称、地址和分类是入库、去重和后续简报分组的稳定上下文。</p>
     */
    public record FeedSource(
            String name,
            URI url,
            ArticleCategory category,
            boolean enabled
    ) {
        public FeedSource {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Feed source name must not be blank");
            }
            if (url == null) {
                throw new IllegalArgumentException("Feed source url must not be null");
            }
            if (category == null) {
                throw new IllegalArgumentException("Feed source category must not be null");
            }
        }
    }
}
