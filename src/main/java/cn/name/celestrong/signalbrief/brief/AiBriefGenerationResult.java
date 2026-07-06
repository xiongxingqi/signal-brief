package cn.name.celestrong.signalbrief.brief;

import java.time.Instant;

public record AiBriefGenerationResult(
        Instant startInclusive,
        Instant endExclusive,
        String draftMarkdown,
        String summaryMarkdown
) {
}
