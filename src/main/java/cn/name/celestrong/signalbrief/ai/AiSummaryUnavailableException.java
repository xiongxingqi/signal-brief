package cn.name.celestrong.signalbrief.ai;

/**
 * AI 摘要能力未启用或配置不完整时抛出的业务异常。
 */
public class AiSummaryUnavailableException extends AiSummaryException {

    public AiSummaryUnavailableException(String message) {
        super(message);
    }
}
