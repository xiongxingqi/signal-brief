package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Markdown 简报生成请求")
public record MarkdownBriefRequest(
        @Schema(description = "查询窗口开始时间，包含该时刻", example = "2026-07-01T00:00:00Z")
        Instant startInclusive,

        @Schema(description = "查询窗口结束时间，不包含该时刻", example = "2026-07-16T00:00:00Z")
        Instant endExclusive
) {
}
