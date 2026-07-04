package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefMarkdownRendererTest {

    private final BriefMarkdownRenderer renderer = new BriefMarkdownRenderer();

    @Test
    void rendersEmptyBrief() {
        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T23:59:59Z"),
                null
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 23:59:59 UTC

                本期暂无候选文章。
                """, markdown);
    }

    @Test
    void groupsArticlesByCategoryAndKeepsInputOrderWithinGroup() {
        Article firstJava = article(
                "InfoQ",
                ArticleCategory.JAVA,
                "Java 25 发布",
                "https://example.com/java-25",
                Instant.parse("2026-07-02T09:15:30Z"),
                "新版本发布摘要。"
        );
        Article ai = article(
                "AI Daily",
                ArticleCategory.AI,
                "模型更新",
                "   ",
                null,
                "AI 新闻摘要。"
        );
        Article secondJava = article(
                "Baeldung",
                ArticleCategory.JAVA,
                "虚拟线程实践",
                "https://example.com/virtual-thread",
                Instant.parse("2026-07-03T10:00:00Z"),
                "   "
        );

        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                List.of(firstJava, ai, secondJava)
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

                ## JAVA

                ### InfoQ: Java 25 发布
                - 来源：InfoQ
                - 发布时间：2026-07-02 09:15:30 UTC
                - 链接：https://example.com/java-25
                新版本发布摘要。

                ### Baeldung: 虚拟线程实践
                - 来源：Baeldung
                - 发布时间：2026-07-03 10:00:00 UTC
                - 链接：https://example.com/virtual-thread
                暂无摘要。

                ## AI

                ### AI Daily: 模型更新
                - 来源：AI Daily
                - 发布时间：未知
                AI 新闻摘要。
                """, markdown);
    }

    @Test
    void escapesMarkdownControlCharactersInTextFields() {
        Article article = article(
                "# Source_[A]",
                ArticleCategory.INDUSTRY,
                "> Title `hot` *now*",
                "https://example.com/a_[b]*c",
                Instant.parse("2026-07-04T08:00:00Z"),
                """
                        - 摘要_[1]
                        # 第二行 `code`
                        > 第三行 *重点*
                        C:\\tmp\\brief
                        """
        );

        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                List.of(article)
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

                ## INDUSTRY

                ### \\# Source\\_\\[A\\]: \\> Title \\`hot\\` \\*now\\*
                - 来源：\\# Source\\_\\[A\\]
                - 发布时间：2026-07-04 08:00:00 UTC
                - 链接：https://example.com/a_[b]*c
                \\- 摘要\\_\\[1\\]
                \\# 第二行 \\`code\\`
                \\> 第三行 \\*重点\\*
                C:\\\\tmp\\\\brief
                """, markdown);
    }

    private Article article(
            String sourceName,
            ArticleCategory category,
            String title,
            String url,
            Instant publishedAt,
            String summary
    ) {
        return new Article(
                1L,
                sourceName,
                "https://example.com/feed.xml",
                category,
                title,
                url,
                "guid",
                publishedAt,
                summary,
                "hash",
                Instant.parse("2026-07-04T00:00:00Z"),
                Instant.parse("2026-07-04T00:00:00Z")
        );
    }
}
