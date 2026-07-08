package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.content.HtmlContentCleaner;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import org.jdom2.Element;
import org.springframework.stereotype.Component;

/**
 * 从 ROME 条目模型中提取短摘要和正文片段。
 *
 * <p>字段选择逻辑集中在这里，避免解析器、入库服务和后续 AI 输入各自理解 RSS / Atom 差异。</p>
 */
@Component
public class FeedEntryContentExtractor {

    private static final String RSS_CONTENT_NAMESPACE = "http://purl.org/rss/1.0/modules/content/";

    private final HtmlContentCleaner cleaner;

    public FeedEntryContentExtractor(HtmlContentCleaner cleaner) {
        this.cleaner = cleaner;
    }

    public ExtractedFeedContent extract(SyndEntry entry) {
        String summary = cleanContent(entry.getDescription());
        String content = firstContentText(entry);
        return new ExtractedFeedContent(summary == null ? content : summary, content);
    }

    private String firstContentText(SyndEntry entry) {
        // Atom content 和部分 RSS 扩展内容会被 ROME 映射到 contents，优先使用该标准抽象。
        for (SyndContent content : entry.getContents()) {
            String text = cleanContent(content);
            if (text != null) {
                return text;
            }
        }
        return contentEncodedText(entry);
    }

    private String contentEncodedText(SyndEntry entry) {
        // RSS content:encoded 并不总能进入 getContents()，需要从 foreign markup 兜底读取。
        for (Element element : entry.getForeignMarkup()) {
            if ("encoded".equals(element.getName())
                    && RSS_CONTENT_NAMESPACE.equals(element.getNamespaceURI())) {
                String text = cleaner.cleanToText(element.getText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String cleanContent(SyndContent content) {
        return content == null ? null : cleaner.cleanToText(content.getValue());
    }
}
