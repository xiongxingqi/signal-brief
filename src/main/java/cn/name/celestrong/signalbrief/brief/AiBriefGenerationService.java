package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * 生成 AI 摘要但不落库的应用服务。
 *
 * <p>该服务用于手动预览链路；需要审计和后续邮件发送时应使用 {@link BriefArchiveService}。</p>
 */
@Service
public class AiBriefGenerationService {

    private final BriefGenerationService briefGenerationService;
    private final AiSummaryService aiSummaryService;

    public AiBriefGenerationService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService
    ) {
        this.briefGenerationService = Objects.requireNonNull(
                briefGenerationService,
                "briefGenerationService must not be null"
        );
        this.aiSummaryService = Objects.requireNonNull(aiSummaryService, "aiSummaryService must not be null");
    }

    /**
     * 先生成确定性 Markdown 草稿，再把草稿交给 AI 摘要服务。
     */
    public AiBriefGenerationResult generate(Instant startInclusive, Instant endExclusive) {
        aiSummaryService.requireAvailable();
        String draftMarkdown = briefGenerationService.generate(startInclusive, endExclusive);
        String summaryMarkdown = aiSummaryService.summarizeMarkdown(draftMarkdown);
        return new AiBriefGenerationResult(startInclusive, endExclusive, draftMarkdown, summaryMarkdown);
    }
}
