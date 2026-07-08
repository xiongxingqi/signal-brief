package cn.name.celestrong.signalbrief.brief;

/**
 * 简报归档记录不存在时抛出的业务异常。
 */
public class BriefGenerationNotFoundException extends RuntimeException {

    public BriefGenerationNotFoundException(String message) {
        super(message);
    }
}
