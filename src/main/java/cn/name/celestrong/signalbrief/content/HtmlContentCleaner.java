package cn.name.celestrong.signalbrief.content;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * 通用 HTML 内容清洗器。
 *
 * <p>该组件不感知 RSS / Atom 来源，后续网页正文抓取、AI 输入准备等场景也可以复用。</p>
 */
@Component
public class HtmlContentCleaner {

    /**
     * 将 HTML 或纯文本片段转成适合入库和摘要输入的纯文本。
     */
    public String cleanToText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        Document document = Jsoup.parseBodyFragment(value);
        document.select("script, style, noscript").remove();
        return StringUtils.trimToNull(document.body().text());
    }
}
