package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.ai.AiSummaryException;
import cn.name.celestrong.signalbrief.ai.AiSummaryUnavailableException;
import cn.name.celestrong.signalbrief.brief.BriefArchiveGenerationException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotReadyException;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunNotFoundException;
import cn.name.celestrong.signalbrief.mail.BriefMailUnavailableException;
import org.springframework.beans.TypeMismatchException;
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

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<InternalApiErrorResponse> handleTypeMismatch() {
        return ResponseEntity.badRequest().body(new InternalApiErrorResponse("请求参数格式不正确"));
    }

    @ExceptionHandler(RssIngestionRunNotFoundException.class)
    public ResponseEntity<InternalApiErrorResponse> handleRunNotFound(RssIngestionRunNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BriefGenerationNotFoundException.class)
    public ResponseEntity<InternalApiErrorResponse> handleBriefGenerationNotFound(BriefGenerationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BriefGenerationNotReadyException.class)
    public ResponseEntity<InternalApiErrorResponse> handleBriefGenerationNotReady(BriefGenerationNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AiSummaryUnavailableException.class)
    public ResponseEntity<InternalApiErrorResponse> handleAiSummaryUnavailable(AiSummaryUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BriefMailUnavailableException.class)
    public ResponseEntity<InternalApiErrorResponse> handleBriefMailUnavailable(BriefMailUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BriefArchiveGenerationException.class)
    public ResponseEntity<BriefArchiveErrorResponse> handleBriefArchiveGenerationException(
            BriefArchiveGenerationException ex
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new BriefArchiveErrorResponse("AI 摘要生成失败", ex.briefGenerationId()));
    }

    @ExceptionHandler(AiSummaryException.class)
    public ResponseEntity<InternalApiErrorResponse> handleAiSummaryException() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new InternalApiErrorResponse("AI 摘要生成失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InternalApiErrorResponse> handleUnexpectedException() {
        // 内部 API 对调用方隐藏未分类异常细节，具体堆栈留给服务端日志和测试排查。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new InternalApiErrorResponse("内部接口执行失败"));
    }
}
