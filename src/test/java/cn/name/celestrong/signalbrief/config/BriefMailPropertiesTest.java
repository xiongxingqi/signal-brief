package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefMailPropertiesTest {

    @Test
    void defaultsToDisabledWithNoRecipients() throws Exception {
        BriefMailProperties properties = bind("""
                signal-brief:
                  mail:
                    enabled: false
                """);

        assertFalse(properties.enabled());
        assertEquals(List.of(), properties.recipients());
        assertEquals("SignalBrief 技术半月报", properties.subjectPrefix());
    }

    @Test
    void requiresFromWhenEnabled() {
        BindException exception = assertThrows(BindException.class, () -> bind("""
                signal-brief:
                  mail:
                    enabled: true
                    recipients: a@example.com
                """));

        assertTrue(containsMessage(exception, "signal-brief.mail.from"), () -> messages(exception));
    }

    @Test
    void requiresRecipientsWhenEnabled() {
        BindException exception = assertThrows(BindException.class, () -> bind("""
                signal-brief:
                  mail:
                    enabled: true
                    from: noreply@example.com
                    recipients:
                      - ""
                      - " "
                """));

        assertTrue(containsMessage(exception, "signal-brief.mail.recipients"), () -> messages(exception));
    }

    @Test
    void trimsRecipientsAndDropsBlankValues() throws Exception {
        BriefMailProperties properties = bind("""
                signal-brief:
                  mail:
                    enabled: true
                    from: noreply@example.com
                    recipients:
                      - " a@example.com "
                      - ""
                      - "b@example.com"
                """);

        assertEquals(List.of("a@example.com", "b@example.com"), properties.recipients());
    }

    @Test
    void bindsCommaSeparatedRecipientsScalar() throws Exception {
        BriefMailProperties properties = bind("""
                signal-brief:
                  mail:
                    enabled: true
                    from: noreply@example.com
                    recipients: " a@example.com, , b@example.com "
                """);

        assertEquals(List.of("a@example.com", "b@example.com"), properties.recipients());
    }

    private static BriefMailProperties bind(String yaml) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        return Binder.get(environment)
                .bind("signal-brief.mail", Bindable.of(BriefMailProperties.class))
                .orElseThrow(IllegalStateException::new);
    }

    private static boolean containsMessage(Throwable throwable, String message) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String messages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!messages.isEmpty()) {
                messages.append(System.lineSeparator());
            }
            messages.append(current.getClass().getName()).append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return messages.toString();
    }
}
