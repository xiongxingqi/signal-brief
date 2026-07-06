package cn.name.celestrong.signalbrief.ai;

import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * AI 摘要应用服务。
 *
 * <p>负责开关校验、编辑约束和异常归一，避免上层 HTTP 入口直接感知 Provider 细节。</p>
 */
@Service
public class AiSummaryService {

    /*
     * 提示词在代码中固定，便于审查摘要口径；运行时配置只控制 Provider 连接参数。
     */
    private static final String SYSTEM_PROMPT = """
            你是 SignalBrief 中文技术半月报编辑。
            读者是 Java 后端开发者。
            请基于 Markdown 草稿生成摘要版 Markdown。
            保留关键原文链接。
            保留草稿中已有 Markdown 链接的 URL，不伪造新链接，不要改变链接目标。
            摘要条目仍应能追溯到原文链接。
            不编造来源、版本、日期或影响范围。
            关注变更、影响和维护者需要采取的行动。
            """;

    private final AiSummaryProperties properties;
    private final AiSummaryClient aiSummaryClient;

    public AiSummaryService(AiSummaryProperties properties, AiSummaryClient aiSummaryClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.aiSummaryClient = Objects.requireNonNull(aiSummaryClient, "aiSummaryClient must not be null");
    }

    /**
     * 在生成草稿或创建归档前先做可用性检查，避免无意义的候选文章查询和持久化副作用。
     */
    public void requireAvailable() {
        if (!properties.enabled()) {
            throw new AiSummaryUnavailableException("AI 摘要能力未启用");
        }
    }

    /**
     * 将确定性 Markdown 草稿压缩为摘要版 Markdown。
     */
    public String summarizeMarkdown(String markdownDraft) {
        requireAvailable();
        if (StringUtils.isBlank(markdownDraft)) {
            throw new IllegalArgumentException("Markdown 简报草稿不能为空");
        }

        String summaryMarkdown = summarize(markdownDraft);
        if (StringUtils.isBlank(summaryMarkdown)) {
            throw new AiSummaryException("AI 摘要结果为空");
        }
        return summaryMarkdown.strip();
    }

    private String summarize(String markdownDraft) {
        try {
            return aiSummaryClient.summarize(SYSTEM_PROMPT, userContent(markdownDraft));
        } catch (AiSummaryException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiSummaryException("AI 摘要生成失败", ex);
        }
    }

    private String userContent(String markdownDraft) {
        return """
                请基于以下 Markdown 草稿生成 SignalBrief 摘要版 Markdown：

                %s
                """.formatted(markdownDraft);
    }
}
