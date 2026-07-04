package cn.name.celestrong.signalbrief.feed;

/**
 * Feed 解析失败异常。
 *
 * <p>用于把 RSS / Atom 格式错误和解析器异常统一包装到入库链路中。</p>
 */
public class FeedParseException extends RuntimeException {

    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
