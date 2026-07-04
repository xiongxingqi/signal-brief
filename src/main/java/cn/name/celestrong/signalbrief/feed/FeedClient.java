package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;

/**
 * RSS / Atom 内容获取边界。
 *
 * <p>实现负责完成协议访问和错误包装；返回的输入流由调用方关闭。</p>
 */
public interface FeedClient {

    /**
     * 获取指定源的原始 feed 内容。
     *
     * <p>不要在该边界提前把响应体解码成字符串，XML 解析器需要自行识别 BOM 和 XML 声明中的编码。</p>
     */
    InputStream fetch(FeedProperties.FeedSource source);
}
