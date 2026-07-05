package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部手动触发入口。
 *
 * <p>该入口只做 HTTP 协议适配，具体入库和简报生成逻辑继续委托应用服务。</p>
 */
@Tag(name = "内部手动触发", description = "RSS 入库和 Markdown 简报草稿的手动触发接口")
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerController {

    private final FeedIngestionService feedIngestionService;
    private final BriefGenerationService briefGenerationService;

    public ManualTriggerController(
            FeedIngestionService feedIngestionService,
            BriefGenerationService briefGenerationService
    ) {
        this.feedIngestionService = feedIngestionService;
        this.briefGenerationService = briefGenerationService;
    }

    @Operation(summary = "触发 RSS 入库", description = "立即抓取所有已启用 RSS / Atom 源并执行去重入库。")
    @ApiResponse(
            responseCode = "200",
            description = "返回本次入库统计",
            content = @Content(schema = @Schema(implementation = FeedIngestionResult.class))
    )
    @PostMapping("/ingestions/rss")
    public FeedIngestionResult triggerRssIngestion() {
        return feedIngestionService.ingestEnabledFeeds();
    }

    @Operation(summary = "生成 Markdown 简报草稿", description = "按半开时间窗口查询候选文章并生成 Markdown 草稿。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回 Markdown 简报草稿",
                    content = @Content(schema = @Schema(implementation = MarkdownBriefResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求体或时间窗口非法",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "内部接口执行失败",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @PostMapping("/briefs/markdown")
    public MarkdownBriefResponse generateMarkdownBrief(@RequestBody MarkdownBriefRequest request) {
        if (request == null || request.startInclusive() == null || request.endExclusive() == null) {
            throw new IllegalArgumentException("startInclusive 和 endExclusive 必须提供");
        }

        String markdown = briefGenerationService.generate(request.startInclusive(), request.endExclusive());
        return new MarkdownBriefResponse(request.startInclusive(), request.endExclusive(), markdown);
    }
}
