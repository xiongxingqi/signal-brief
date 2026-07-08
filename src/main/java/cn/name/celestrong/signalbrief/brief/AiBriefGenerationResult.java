package cn.name.celestrong.signalbrief.brief;

import java.time.Instant;

/**
 * 手动 AI 摘要预览结果。
 *
 * <p>同时返回草稿和摘要，便于对照 prompt 效果；该结果本身不代表已归档。</p>
 */
public record AiBriefGenerationResult(
        Instant startInclusive,
        Instant endExclusive,
        String draftMarkdown,
        String summaryMarkdown
) {
}
