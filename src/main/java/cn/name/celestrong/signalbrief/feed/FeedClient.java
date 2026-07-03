package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;

public interface FeedClient {

    InputStream fetch(FeedProperties.FeedSource source);
}
