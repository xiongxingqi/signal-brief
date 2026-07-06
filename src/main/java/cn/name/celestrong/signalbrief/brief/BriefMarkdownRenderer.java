package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将候选文章渲染为稳定的 Markdown 简报。
 */
@Component
public class BriefMarkdownRenderer {

    private static final DateTimeFormatter UTC_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public String render(Instant startInclusive, Instant endExclusive, List<Article> articles) {
        List<Article> safeArticles = articles == null ? List.of() : articles;
        StringBuilder markdown = new StringBuilder();
        markdown.append("# SignalBrief 技术半月报\n\n");
        markdown.append("时间范围：")
                .append(formatTime(startInclusive))
                .append(" 至 ")
                .append(formatTime(endExclusive))
                .append("\n\n");

        if (safeArticles.isEmpty()) {
            markdown.append("本期暂无候选文章。\n");
            return markdown.toString();
        }

        Map<ArticleCategory, List<Article>> groupedArticles = groupByCategory(safeArticles);
        boolean firstCategory = true;
        for (Map.Entry<ArticleCategory, List<Article>> entry : groupedArticles.entrySet()) {
            if (!firstCategory) {
                markdown.append("\n");
            }
            markdown.append("## ")
                    .append(entry.getKey())
                    .append("\n\n");
            List<Article> groupArticles = entry.getValue();
            for (int index = 0; index < groupArticles.size(); index++) {
                if (index > 0) {
                    markdown.append("\n");
                }
                appendArticle(markdown, groupArticles.get(index));
            }
            firstCategory = false;
        }
        return markdown.toString();
    }

    private Map<ArticleCategory, List<Article>> groupByCategory(List<Article> articles) {
        Map<ArticleCategory, List<Article>> groupedArticles = new LinkedHashMap<>();
        for (Article article : articles) {
            groupedArticles.computeIfAbsent(article.category(), ignored -> new ArrayList<>()).add(article);
        }
        return groupedArticles;
    }

    private void appendArticle(StringBuilder markdown, Article article) {
        String sourceName = escapeMarkdownText(article.sourceName());
        markdown.append("### ")
                .append(sourceName)
                .append(": ")
                .append(escapeMarkdownText(article.title()))
                .append("\n");
        markdown.append("- 来源：")
                .append(sourceName)
                .append("\n");
        markdown.append("- 发布时间：")
                .append(article.publishedAt() == null ? "未知" : formatTime(article.publishedAt()))
                .append("\n");
        if (StringUtils.isNotBlank(article.url())) {
            markdown.append("- 链接：")
                    .append(article.url())
                    .append("\n");
        }
        String summary = StringUtils.trimToNull(article.summary());
        markdown.append("\n");
        markdown.append(summary == null ? "暂无摘要。" : escapeMarkdownText(summary))
                .append("\n");
    }

    private String formatTime(Instant instant) {
        return UTC_TIME_FORMATTER.format(instant);
    }

    /**
     * 只转义来自外部文章内容的 Markdown 结构符，保留简报模板自身的标题和列表结构。
     */
    private String escapeMarkdownText(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(text.length());
        int lineStartIndex = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (isMarkdownBlockPrefix(text, lineStartIndex, index)
                    || current == '\\'
                    || current == '`'
                    || current == '*'
                    || current == '_'
                    || current == '['
                    || current == ']') {
                escaped.append('\\');
            }

            escaped.append(current);
            if (current == '\n') {
                lineStartIndex = index + 1;
            }
        }
        return escaped.toString();
    }

    private boolean isMarkdownBlockPrefix(String text, int lineStartIndex, int index) {
        // CommonMark 允许最多三个前导空格后仍形成块级语法，所以这里不能只检查行首字符。
        if (index < lineStartIndex || text.charAt(index) == ' ') {
            return false;
        }

        int leadingSpaces = index - lineStartIndex;
        if (leadingSpaces > 3 || !isOnlySpaces(text, lineStartIndex, index)) {
            return false;
        }

        char current = text.charAt(index);
        return current == '#'
                || current == '>'
                || isUnorderedListPrefix(text, index)
                || isOrderedListPrefix(text, index)
                || isThematicBreak(text, index);
    }

    private boolean isOnlySpaces(String text, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (text.charAt(index) != ' ') {
                return false;
            }
        }
        return true;
    }

    private boolean isUnorderedListPrefix(String text, int index) {
        char current = text.charAt(index);
        return (current == '-' || current == '+' || current == '*')
                && index + 1 < text.length()
                && text.charAt(index + 1) == ' ';
    }

    private boolean isOrderedListPrefix(String text, int index) {
        if (!Character.isDigit(text.charAt(index))) {
            return false;
        }

        int cursor = index;
        while (cursor < text.length() && Character.isDigit(text.charAt(cursor))) {
            cursor++;
        }
        return cursor + 1 < text.length()
                && (text.charAt(cursor) == '.' || text.charAt(cursor) == ')')
                && text.charAt(cursor + 1) == ' ';
    }

    private boolean isThematicBreak(String text, int index) {
        char marker = text.charAt(index);
        if (marker != '-' && marker != '*' && marker != '_') {
            return false;
        }

        int markerCount = 0;
        int cursor = index;
        while (cursor < text.length() && text.charAt(cursor) != '\n') {
            char current = text.charAt(cursor);
            if (current == marker) {
                markerCount++;
            } else if (current != ' ') {
                return false;
            }
            cursor++;
        }
        return markerCount >= 3;
    }
}
