package cn.name.celestrong.signalbrief.feed;

/**
 * Feed 抓取失败分类。
 *
 * <p>分类会写入运行明细，用于区分远端 HTTP 状态、客户端 I/O 和未预期错误。</p>
 */
public enum FeedFetchFailureType {
    HTTP_STATUS,
    CLIENT_IO,
    UNEXPECTED
}
