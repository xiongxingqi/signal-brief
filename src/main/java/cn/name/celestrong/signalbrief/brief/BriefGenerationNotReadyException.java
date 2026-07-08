package cn.name.celestrong.signalbrief.brief;

/**
 * 简报归档状态尚不可执行后续操作时抛出的业务异常。
 */
public class BriefGenerationNotReadyException extends RuntimeException {

    public BriefGenerationNotReadyException(String message) {
        super(message);
    }
}
