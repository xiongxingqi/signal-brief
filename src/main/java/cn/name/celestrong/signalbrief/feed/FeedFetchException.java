package cn.name.celestrong.signalbrief.feed;

/**
 * Feed 抓取失败异常。
 *
 * <p>用于把 HTTP 状态、网络错误等底层异常统一包装到入库链路中。</p>
 */
public class FeedFetchException extends RuntimeException {

    public FeedFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
