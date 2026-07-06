package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.AiBriefGenerationResult;
import cn.name.celestrong.signalbrief.brief.AiBriefGenerationService;
import cn.name.celestrong.signalbrief.brief.BriefArchiveService;
import cn.name.celestrong.signalbrief.brief.BriefGeneration;
import cn.name.celestrong.signalbrief.brief.BriefGenerationQueryService;
import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionOperations;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.IngestionTriggerType;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRun;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunDetail;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunQueryService;
import cn.name.celestrong.signalbrief.mail.BriefMailDeliveryResult;
import cn.name.celestrong.signalbrief.mail.BriefMailDeliveryQueryService;
import cn.name.celestrong.signalbrief.mail.BriefMailDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部手动触发入口。
 *
 * <p>该入口只做 HTTP 协议适配，具体入库和简报生成逻辑继续委托应用服务。</p>
 */
@Tag(name = "内部手动触发", description = "RSS 入库、Markdown 简报草稿、AI 摘要、简报归档和邮件发送的手动触发接口")
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerController {

    private final FeedIngestionOperations feedIngestionOperations;
    private final BriefGenerationService briefGenerationService;
    private final AiBriefGenerationService aiBriefGenerationService;
    private final BriefArchiveService briefArchiveService;
    private final BriefGenerationQueryService briefGenerationQueryService;
    private final BriefMailDeliveryService briefMailDeliveryService;
    private final BriefMailDeliveryQueryService briefMailDeliveryQueryService;
    private final RssIngestionRunQueryService runQueryService;

    public ManualTriggerController(
            FeedIngestionOperations feedIngestionOperations,
            BriefGenerationService briefGenerationService,
            AiBriefGenerationService aiBriefGenerationService,
            BriefArchiveService briefArchiveService,
            BriefGenerationQueryService briefGenerationQueryService,
            BriefMailDeliveryService briefMailDeliveryService,
            BriefMailDeliveryQueryService briefMailDeliveryQueryService,
            RssIngestionRunQueryService runQueryService
    ) {
        this.feedIngestionOperations = feedIngestionOperations;
        this.briefGenerationService = briefGenerationService;
        this.aiBriefGenerationService = aiBriefGenerationService;
        this.briefArchiveService = briefArchiveService;
        this.briefGenerationQueryService = briefGenerationQueryService;
        this.briefMailDeliveryService = briefMailDeliveryService;
        this.briefMailDeliveryQueryService = briefMailDeliveryQueryService;
        this.runQueryService = runQueryService;
    }

    @Operation(summary = "触发 RSS 入库", description = "立即抓取所有已启用 RSS / Atom 源并执行去重入库。")
    @ApiResponse(
            responseCode = "200",
            description = "返回本次入库统计",
            content = @Content(schema = @Schema(implementation = FeedIngestionResult.class))
    )
    @PostMapping("/ingestions/rss")
    public FeedIngestionResult triggerRssIngestion() {
        return feedIngestionOperations.ingestEnabledFeeds(IngestionTriggerType.MANUAL);
    }

    @Operation(summary = "查询 RSS 入库运行记录", description = "按开始时间倒序查询最近的 RSS 入库运行记录。")
    @ApiResponse(
            responseCode = "200",
            description = "返回 RSS 入库运行记录列表",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RssIngestionRun.class)))
    )
    @GetMapping("/ingestions/rss/runs")
    public List<RssIngestionRun> listRssIngestionRuns(@RequestParam(required = false) Integer limit) {
        return runQueryService.findRecentRuns(limit);
    }

    @Operation(summary = "查询 RSS 入库运行明细", description = "查询单次 RSS 入库运行记录及各源执行明细。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回 RSS 入库运行明细",
                    content = @Content(schema = @Schema(implementation = RssIngestionRunDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "入库运行记录不存在",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @GetMapping("/ingestions/rss/runs/{id}")
    public RssIngestionRunDetail getRssIngestionRun(@PathVariable Long id) {
        return runQueryService.findRunDetail(id);
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
        validateBriefWindowRequest(request);

        String markdown = briefGenerationService.generate(request.startInclusive(), request.endExclusive());
        return new MarkdownBriefResponse(request.startInclusive(), request.endExclusive(), markdown);
    }

    @Operation(summary = "生成 AI 摘要简报", description = "按半开时间窗口生成确定性 Markdown 草稿，并调用 AI 生成摘要版 Markdown。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回 Markdown 草稿和 AI 摘要",
                    content = @Content(schema = @Schema(implementation = AiSummaryBriefResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求体或时间窗口非法",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "AI 摘要未启用",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI Provider 调用失败",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @PostMapping("/briefs/ai-summary")
    public AiSummaryBriefResponse generateAiSummaryBrief(@RequestBody MarkdownBriefRequest request) {
        validateBriefWindowRequest(request);

        AiBriefGenerationResult result = aiBriefGenerationService.generate(
                request.startInclusive(),
                request.endExclusive()
        );
        return new AiSummaryBriefResponse(
                result.startInclusive(),
                result.endExclusive(),
                result.draftMarkdown(),
                result.summaryMarkdown()
        );
    }

    @Operation(summary = "归档 AI 摘要简报", description = "按半开时间窗口生成 Markdown 草稿和 AI 摘要，并保存为可发送的简报归档。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回简报归档记录",
                    content = @Content(schema = @Schema(implementation = BriefGeneration.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求体或时间窗口非法",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "AI 摘要未启用",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI Provider 调用失败，响应包含已创建的归档记录 ID",
                    content = @Content(schema = @Schema(implementation = BriefArchiveErrorResponse.class))
            )
    })
    @PostMapping("/briefs/ai-summary/archives")
    public BriefGeneration archiveAiSummaryBrief(@RequestBody MarkdownBriefRequest request) {
        validateBriefWindowRequest(request);

        return briefArchiveService.archiveAiSummary(request.startInclusive(), request.endExclusive());
    }

    @Operation(summary = "查询简报归档列表", description = "按最新记录倒序查询最近的 AI 简报归档。")
    @ApiResponse(
            responseCode = "200",
            description = "返回简报归档列表",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BriefGeneration.class)))
    )
    @GetMapping("/briefs/archives")
    public List<BriefGeneration> listBriefArchives(@RequestParam(required = false) Integer limit) {
        return briefGenerationQueryService.findRecentArchives(limit);
    }

    @Operation(summary = "查询简报归档详情", description = "按归档 ID 查询 AI 简报生成记录。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回简报归档详情",
                    content = @Content(schema = @Schema(implementation = BriefGeneration.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "简报归档记录不存在",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @GetMapping("/briefs/archives/{id}")
    public BriefGeneration getBriefArchive(@PathVariable Long id) {
        return briefGenerationQueryService.findArchive(id);
    }

    @Operation(summary = "发送简报归档邮件", description = "将已成功生成的简报归档发送到配置的收件人列表。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回邮件发送记录",
                    content = @Content(schema = @Schema(implementation = BriefMailDeliveryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "简报归档记录不存在",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "简报归档尚不可发送",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "邮件发送能力不可用",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @PostMapping("/briefs/{id}/mail-deliveries")
    public BriefMailDeliveryResponse deliverArchivedBriefMail(@PathVariable Long id) {
        BriefMailDeliveryResult result = briefMailDeliveryService.deliver(id);
        return new BriefMailDeliveryResponse(result.briefGenerationId(), result.deliveries());
    }

    @Operation(summary = "查询简报邮件发送记录", description = "按归档 ID 查询已创建的邮件发送记录。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回邮件发送记录",
                    content = @Content(schema = @Schema(implementation = BriefMailDeliveryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "简报归档记录不存在",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @GetMapping("/briefs/archives/{id}/mail-deliveries")
    public BriefMailDeliveryResponse listArchivedBriefMailDeliveries(@PathVariable Long id) {
        return new BriefMailDeliveryResponse(id, briefMailDeliveryQueryService.findDeliveries(id));
    }

    private void validateBriefWindowRequest(MarkdownBriefRequest request) {
        if (request == null || request.startInclusive() == null || request.endExclusive() == null) {
            throw new IllegalArgumentException("startInclusive 和 endExclusive 必须提供");
        }
    }
}
