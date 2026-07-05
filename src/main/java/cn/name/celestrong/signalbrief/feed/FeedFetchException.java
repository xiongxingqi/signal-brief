package cn.name.celestrong.signalbrief.feed;

/**
 * Feed 抓取失败异常。
 *
 * <p>用于把 HTTP 状态、网络错误等底层异常统一包装到入库链路中。</p>
 */
public class FeedFetchException extends RuntimeException {

    private final FeedFetchFailureType failureType;
    private final Integer httpStatus;
    private final int attemptCount;
    private final int maxAttempts;

    public FeedFetchException(
            String message,
            FeedFetchFailureType failureType,
            Integer httpStatus,
            int attemptCount,
            int maxAttempts,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.httpStatus = httpStatus;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
    }

    public FeedFetchFailureType failureType() {
        return failureType;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public FeedFetchException withAttempts(int attemptCount, int maxAttempts) {
        return new FeedFetchException(
                getMessage(),
                failureType,
                httpStatus,
                attemptCount,
                maxAttempts,
                getCause()
        );
    }
}
