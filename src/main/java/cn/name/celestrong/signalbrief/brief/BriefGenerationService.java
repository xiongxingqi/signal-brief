package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleQueryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 生成 Markdown 简报的应用服务。
 */
@Service
public class BriefGenerationService {

    private final ArticleQueryService articleQueryService;
    private final BriefMarkdownRenderer briefMarkdownRenderer;

    public BriefGenerationService(
            ArticleQueryService articleQueryService,
            BriefMarkdownRenderer briefMarkdownRenderer
    ) {
        this.articleQueryService = articleQueryService;
        this.briefMarkdownRenderer = briefMarkdownRenderer;
    }

    /**
     * 根据指定半开时间窗口查询候选文章并渲染为 Markdown。
     */
    public String generate(Instant startInclusive, Instant endExclusive) {
        List<Article> articles = articleQueryService.findBriefCandidates(startInclusive, endExclusive);
        return briefMarkdownRenderer.render(startInclusive, endExclusive, articles);
    }
}
