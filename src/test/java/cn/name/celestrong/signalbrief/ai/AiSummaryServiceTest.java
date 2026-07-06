package cn.name.celestrong.signalbrief.ai;

import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSummaryServiceTest {

    @Test
    void delegatesMarkdownDraftToClientWithPrompt() {
        RecordingAiSummaryClient client = new RecordingAiSummaryClient("## AI 摘要");
        AiSummaryService service = new AiSummaryService(enabledProperties(), client);

        String summary = service.summarizeMarkdown("# SignalBrief 技术半月报\n\n## JAVA\n\n内容");

        assertEquals("## AI 摘要", summary);
        assertTrue(client.systemPrompt.contains("Java 后端开发者"));
        assertTrue(client.systemPrompt.contains("不编造来源"));
        assertTrue(client.systemPrompt.contains("不要改变链接目标"));
        assertTrue(client.userContent.contains("# SignalBrief 技术半月报"));
    }

    @Test
    void rejectsCallWhenDisabled() {
        FailingAiSummaryClient client = new FailingAiSummaryClient();
        AiSummaryService service = new AiSummaryService(disabledProperties(), client);

        AiSummaryUnavailableException exception = assertThrows(
                AiSummaryUnavailableException.class,
                () -> service.summarizeMarkdown("# 草稿")
        );

        assertEquals("AI 摘要能力未启用", exception.getMessage());
        assertEquals(0, client.calls);
    }

    @Test
    void wrapsUnexpectedClientFailure() {
        AiSummaryService service = new AiSummaryService(
                enabledProperties(),
                (systemPrompt, userContent) -> {
                    throw new IllegalStateException("client failed");
                }
        );

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> service.summarizeMarkdown("# 草稿")
        );

        assertEquals("AI 摘要生成失败", exception.getMessage());
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
    }

    @Test
    void rejectsBlankProviderOutput() {
        AiSummaryService service = new AiSummaryService(enabledProperties(), new RecordingAiSummaryClient("  "));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> service.summarizeMarkdown("# 草稿")
        );

        assertEquals("AI 摘要结果为空", exception.getMessage());
    }

    @Test
    void rejectsBlankMarkdownDraft() {
        AiSummaryService service = new AiSummaryService(enabledProperties(), new RecordingAiSummaryClient("## AI 摘要"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.summarizeMarkdown("  ")
        );

        assertEquals("Markdown 简报草稿不能为空", exception.getMessage());
    }

    private AiSummaryProperties enabledProperties() {
        return new AiSummaryProperties(
                true,
                "https://api.example.com/v1",
                "test-key",
                "test-model",
                null,
                null,
                null,
                null
        );
    }

    private AiSummaryProperties disabledProperties() {
        return new AiSummaryProperties(false, null, null, null, null, null, null, null);
    }

    private static class RecordingAiSummaryClient implements AiSummaryClient {

        private final String summary;
        private String systemPrompt;
        private String userContent;

        private RecordingAiSummaryClient(String summary) {
            this.summary = summary;
        }

        @Override
        public String summarize(String systemPrompt, String userContent) {
            this.systemPrompt = systemPrompt;
            this.userContent = userContent;
            return summary;
        }
    }

    private static class FailingAiSummaryClient implements AiSummaryClient {

        private int calls;

        @Override
        public String summarize(String systemPrompt, String userContent) {
            calls++;
            throw new AssertionError("disabled 时不应调用 AI Provider");
        }
    }
}
