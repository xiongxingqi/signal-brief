package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;
import java.util.List;

/**
 * Feed 解析边界。
 *
 * <p>解析器只负责把输入流转换为统一文章模型，不负责关闭输入流。</p>
 */
public interface FeedParser {

    /**
     * 解析指定来源的 RSS / Atom 内容。
     */
    List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream);
}
