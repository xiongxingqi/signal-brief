package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

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

    public AiBriefGenerationResult generate(Instant startInclusive, Instant endExclusive) {
        aiSummaryService.requireAvailable();
        String draftMarkdown = briefGenerationService.generate(startInclusive, endExclusive);
        String summaryMarkdown = aiSummaryService.summarizeMarkdown(draftMarkdown);
        return new AiBriefGenerationResult(startInclusive, endExclusive, draftMarkdown, summaryMarkdown);
    }
}
