package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "AI 摘要简报生成响应")
public record AiSummaryBriefResponse(
        @Schema(description = "查询窗口开始时间，包含该时刻")
        Instant startInclusive,

        @Schema(description = "查询窗口结束时间，不包含该时刻")
        Instant endExclusive,

        @Schema(description = "生成的 Markdown 简报草稿")
        String draftMarkdown,

        @Schema(description = "AI 生成的 Markdown 摘要")
        String summaryMarkdown
) {
}
