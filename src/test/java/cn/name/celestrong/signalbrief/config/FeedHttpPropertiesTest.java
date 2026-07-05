package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedHttpPropertiesTest {

    @Test
    void defaultsFeedHttpSettings() {
        FeedHttpProperties properties = new FeedHttpProperties(null, null, null, null);

        assertEquals("signal-brief/0.0.1", properties.userAgent());
        assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(10), properties.readTimeout());
        assertEquals(2, properties.retry().maxAttempts());
        assertEquals(Duration.ofSeconds(1), properties.retry().backoff());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  feed-http:
                    user-agent: signal-brief-test
                    connect-timeout: 5s
                    read-timeout: 20s
                    retry:
                      max-attempts: 3
                      backoff: 250ms
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        FeedHttpProperties properties = Binder.get(environment)
                .bind("signal-brief.feed-http", Bindable.of(FeedHttpProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertEquals("signal-brief-test", properties.userAgent());
        assertEquals(Duration.ofSeconds(5), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(20), properties.readTimeout());
        assertEquals(3, properties.retry().maxAttempts());
        assertEquals(Duration.ofMillis(250), properties.retry().backoff());
    }

    @Test
    void rejectsInvalidRetryAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new FeedHttpProperties(
                null,
                null,
                null,
                new FeedHttpProperties.Retry(0, null)
        ));
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new FeedHttpProperties(
                null,
                Duration.ofSeconds(-1),
                null,
                null
        ));
    }
}
