package cn.name.celestrong.signalbrief.config;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

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
