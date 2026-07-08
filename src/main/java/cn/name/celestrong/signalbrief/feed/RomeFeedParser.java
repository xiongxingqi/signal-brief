package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 基于 ROME 的 RSS / Atom 解析实现。
 *
 * <p>解析时保留原始输入流，让 XmlReader 处理 BOM 和 XML 声明中的编码信息。</p>
 */
@Component
public class RomeFeedParser implements FeedParser {

    private final FeedEntryContentExtractor contentExtractor;

    public RomeFeedParser(FeedEntryContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    @Override
    public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
        try {
            // 不提前解码为 String，避免破坏 GBK 等非 UTF-8 feed 的编码识别。
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(inputStream));
            return feed.getEntries().stream()
                    .map(entry -> toFetchedArticle(source, entry))
                    .toList();
        } catch (Exception ex) {
            throw new FeedParseException("Failed to parse feed source: " + source.name(), ex);
        }
    }

    private FetchedArticle toFetchedArticle(FeedProperties.FeedSource source, SyndEntry entry) {
        ExtractedFeedContent content = contentExtractor.extract(entry);
        return new FetchedArticle(
                source.name(),
                source.url(),
                source.category(),
                clean(entry.getTitle()),
                clean(entry.getLink()),
                clean(entry.getUri()),
                toInstant(entry.getPublishedDate(), entry.getUpdatedDate()),
                content.summaryText(),
                content.contentText()
        );
    }

    private Instant toInstant(Date publishedDate, Date updatedDate) {
        Date date = publishedDate != null ? publishedDate : updatedDate;
        return date == null ? null : date.toInstant();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
