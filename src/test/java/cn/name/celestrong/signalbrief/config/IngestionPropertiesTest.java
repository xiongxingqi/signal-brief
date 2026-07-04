package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionPropertiesTest {

    @Test
    void defaultsDisableScheduledIngestion() {
        IngestionProperties properties = new IngestionProperties(false, null);

        assertFalse(properties.enabled());
        assertEquals("0 0 6 1,16 * *", properties.cron());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  ingestion:
                    enabled: true
                    cron: "0 30 7 * * *"
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        IngestionProperties properties = Binder.get(environment)
                .bind("signal-brief.ingestion", Bindable.of(IngestionProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertTrue(properties.enabled());
        assertEquals("0 30 7 * * *", properties.cron());
    }
}
