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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSummaryPropertiesTest {

    @Test
    void defaultsDisableAiSummary() {
        AiSummaryProperties properties = new AiSummaryProperties(false, null, null, null, null, null, null, null);

        assertFalse(properties.enabled());
        assertEquals("", properties.baseUrl());
        assertEquals("", properties.apiKey());
        assertEquals("", properties.model());
        assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(30), properties.readTimeout());
        assertEquals(0.2, properties.temperature());
        assertEquals(2000, properties.maxOutputTokens());
    }

    @Test
    void stripsTextSettings() {
        AiSummaryProperties properties = new AiSummaryProperties(
                true,
                " https://api.example.com/v1 ",
                " test-key ",
                " test-model ",
                null,
                null,
                null,
                null
        );

        assertEquals("https://api.example.com/v1", properties.baseUrl());
        assertEquals("test-key", properties.apiKey());
        assertEquals("test-model", properties.model());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  ai-summary:
                    enabled: true
                    base-url: https://api.example.com/v1
                    api-key: test-key
                    model: test-model
                    connect-timeout: 5s
                    read-timeout: 45s
                    temperature: 0.3
                    max-output-tokens: 1200
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        AiSummaryProperties properties = Binder.get(environment)
                .bind("signal-brief.ai-summary", Bindable.of(AiSummaryProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertTrue(properties.enabled());
        assertEquals("https://api.example.com/v1", properties.baseUrl());
        assertEquals("test-key", properties.apiKey());
        assertEquals("test-model", properties.model());
        assertEquals(Duration.ofSeconds(5), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(45), properties.readTimeout());
        assertEquals(0.3, properties.temperature());
        assertEquals(1200, properties.maxOutputTokens());
    }

    @Test
    void rejectsEnabledWithoutBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                true,
                null,
                "test-key",
                "test-model",
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void rejectsEnabledWithoutApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                true,
                "https://api.example.com/v1",
                null,
                "test-model",
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void rejectsEnabledWithoutModel() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                true,
                "https://api.example.com/v1",
                "test-key",
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void rejectsNegativeConnectTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                Duration.ofSeconds(-1),
                null,
                null,
                null
        ));
    }

    @Test
    void rejectsNegativeReadTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                null,
                Duration.ofSeconds(-1),
                null,
                null
        ));
    }

    @Test
    void rejectsTemperatureBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                null,
                null,
                -0.1,
                null
        ));
    }

    @Test
    void rejectsTemperatureAboveTwo() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                null,
                null,
                2.1,
                null
        ));
    }

    @Test
    void rejectsTemperatureNan() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                null,
                null,
                Double.NaN,
                null
        ));
    }

    @Test
    void rejectsZeroMaxOutputTokens() {
        assertThrows(IllegalArgumentException.class, () -> new AiSummaryProperties(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        ));
    }
}
