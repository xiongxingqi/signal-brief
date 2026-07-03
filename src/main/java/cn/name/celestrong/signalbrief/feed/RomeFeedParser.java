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

@Component
public class RomeFeedParser implements FeedParser {

    @Override
    public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(inputStream));
            return feed.getEntries().stream()
                    .map(entry -> toFetchedArticle(source, entry))
                    .toList();
        } catch (Exception ex) {
            throw new FeedParseException("Failed to parse feed source: " + source.name(), ex);
        }
    }

    private FetchedArticle toFetchedArticle(FeedProperties.FeedSource source, SyndEntry entry) {
        return new FetchedArticle(
                source.name(),
                source.url(),
                source.category(),
                clean(entry.getTitle()),
                clean(entry.getLink()),
                clean(entry.getUri()),
                toInstant(entry.getPublishedDate(), entry.getUpdatedDate()),
                entry.getDescription() == null ? null : clean(entry.getDescription().getValue())
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
