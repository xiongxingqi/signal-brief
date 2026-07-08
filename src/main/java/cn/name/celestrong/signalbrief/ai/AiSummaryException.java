package cn.name.celestrong.signalbrief.ai;

/**
 * AI 摘要链路的统一运行时异常。
 */
public class AiSummaryException extends RuntimeException {

    public AiSummaryException(String message) {
        super(message);
    }

    public AiSummaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
