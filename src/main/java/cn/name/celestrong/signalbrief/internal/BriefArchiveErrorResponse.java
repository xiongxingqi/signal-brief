package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "简报归档错误响应")
public record BriefArchiveErrorResponse(
        @Schema(description = "错误说明", example = "AI 摘要生成失败")
        String message,

        @Schema(description = "已创建的简报归档记录 ID", example = "100")
        Long briefGenerationId
) {
}
