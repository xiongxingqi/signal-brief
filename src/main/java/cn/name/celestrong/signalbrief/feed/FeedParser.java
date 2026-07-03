package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;
import java.util.List;

public interface FeedParser {

    List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream);
}
