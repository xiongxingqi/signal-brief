package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "内部 API 错误响应")
public record InternalApiErrorResponse(
        @Schema(description = "错误说明", example = "请求体格式不正确")
        String message
) {
}
