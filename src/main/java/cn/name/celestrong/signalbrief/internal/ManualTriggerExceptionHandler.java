package cn.name.celestrong.signalbrief.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 内部手动触发 API 的 HTTP 错误映射。
 */
@RestControllerAdvice(assignableTypes = ManualTriggerController.class)
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<InternalApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<InternalApiErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(new InternalApiErrorResponse("请求体格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InternalApiErrorResponse> handleUnexpectedException() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new InternalApiErrorResponse("内部接口执行失败"));
    }
}
