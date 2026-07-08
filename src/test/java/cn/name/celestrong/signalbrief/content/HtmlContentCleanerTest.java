package cn.name.celestrong.signalbrief.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 覆盖通用清洗器的输入形态，避免后续为 feed 特例修改时破坏网页正文复用场景。
 */
class HtmlContentCleanerTest {

    private final HtmlContentCleaner cleaner = new HtmlContentCleaner();

    @Test
    void cleansHtmlFragmentToPlainText() {
        String text = cleaner.cleanToText("<p>Hello <strong>Spring Boot</strong></p>");

        assertEquals("Hello Spring Boot", text);
    }

    @Test
    void removesScriptStyleAndNoscriptContent() {
        String text = cleaner.cleanToText("""
                <style>.hidden { display: none; }</style>
                <p>Visible text</p>
                <script>alert('x')</script>
                <noscript>Fallback text</noscript>
                """);

        assertEquals("Visible text", text);
    }

    @Test
    void keepsPlainTextStable() {
        String text = cleaner.cleanToText("  Plain RSS summary  ");

        assertEquals("Plain RSS summary", text);
    }

    @Test
    void returnsNullWhenContentHasNoReadableText() {
        assertNull(cleaner.cleanToText("<p> </p><div></div>"));
    }
}
