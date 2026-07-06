package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 简报归档应用服务。
 *
 * <p>先保存 {@code GENERATING} 记录再调用 AI Provider，让失败也能被审计并返回归档 ID。</p>
 */
@Service
public class BriefArchiveService {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 1_000;
    private static final String DEFAULT_ERROR_SUMMARY = "AI 摘要生成失败";

    private final BriefGenerationService briefGenerationService;
    private final AiSummaryService aiSummaryService;
    private final BriefGenerationMapper mapper;
    private final Clock clock;

    @Autowired
    public BriefArchiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper
    ) {
        this(briefGenerationService, aiSummaryService, mapper, Clock.systemUTC());
    }

    BriefArchiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper,
            Clock clock
    ) {
        this.briefGenerationService = Objects.requireNonNull(
                briefGenerationService,
                "briefGenerationService must not be null"
        );
        this.aiSummaryService = Objects.requireNonNull(aiSummaryService, "aiSummaryService must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成摘要版简报并持久化为可发送归档。
     */
    public BriefGeneration archiveAiSummary(Instant startInclusive, Instant endExclusive) {
        aiSummaryService.requireAvailable();
        String draftMarkdown = briefGenerationService.generate(startInclusive, endExclusive);
        Long id = mapper.insertGenerating(startInclusive, endExclusive, draftMarkdown);

        String summaryMarkdown;
        try {
            summaryMarkdown = aiSummaryService.summarizeMarkdown(draftMarkdown);
        } catch (RuntimeException ex) {
            markFailed(id, ex);
            throw new BriefArchiveGenerationException(id, "AI 摘要归档生成失败", ex);
        }

        int updated = mapper.markSuccess(id, summaryMarkdown, clock.instant());
        if (updated != 1) {
            throw new IllegalStateException("简报归档状态更新失败: " + id);
        }
        return mapper.findById(id)
                .orElseThrow(() -> new IllegalStateException("简报归档记录不存在: " + id));
    }

    private void markFailed(Long id, RuntimeException exception) {
        int updated = mapper.markFailed(id, errorSummary(exception), clock.instant());
        if (updated != 1) {
            throw new IllegalStateException("简报归档失败状态更新失败: " + id, exception);
        }
    }

    private String errorSummary(Exception exception) {
        String message = StringUtils.defaultIfBlank(exception.getMessage(), DEFAULT_ERROR_SUMMARY);
        if (message.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }
}
