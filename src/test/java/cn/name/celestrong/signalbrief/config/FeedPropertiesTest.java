package cn.name.celestrong.signalbrief.config;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedPropertiesTest {

    @Test
    void enabledFeedsReturnsOnlyEnabledSources() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Spring Blog", URI.create("https://spring.io/blog.atom"), ArticleCategory.FRAMEWORK, true),
                new FeedProperties.FeedSource("Disabled", URI.create("https://example.com/rss.xml"), ArticleCategory.INDUSTRY, false)
        ));

        List<FeedProperties.FeedSource> enabledFeeds = properties.enabledFeeds();

        assertEquals(1, enabledFeeds.size());
        assertEquals("Spring Blog", enabledFeeds.getFirst().name());
    }

    @Test
    void rejectsBlankFeedName() {
        assertThrows(IllegalArgumentException.class, () -> new FeedProperties.FeedSource(
                " ",
                URI.create("https://spring.io/blog.atom"),
                ArticleCategory.FRAMEWORK,
                true
        ));
    }

    @Test
    void rejectsMissingUrl() {
        assertThrows(IllegalArgumentException.class, () -> new FeedProperties.FeedSource(
                "Spring Blog",
                null,
                ArticleCategory.FRAMEWORK,
                true
        ));
    }
}
