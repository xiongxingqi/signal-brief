package cn.name.celestrong.signalbrief.ingestion;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RSS 入库运行记录查询服务。
 *
 * <p>查询入口集中控制默认 limit、最大 limit 和 404 语义，避免 Controller 复制规则。</p>
 */
@Service
public class RssIngestionRunQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RssIngestionRunMapper mapper;

    public RssIngestionRunQueryService(RssIngestionRunMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询最近入库批次，限制最大返回数量。
     */
    public List<RssIngestionRun> findRecentRuns(Integer limit) {
        return mapper.findRecentRuns(resolveLimit(limit));
    }

    /**
     * 查询批次详情；批次不存在时抛出业务异常供内部接口映射为 404。
     */
    public RssIngestionRunDetail findRunDetail(Long runId) {
        RssIngestionRun run = mapper.findRunById(runId)
                .orElseThrow(() -> new RssIngestionRunNotFoundException(runId));
        return new RssIngestionRunDetail(run, mapper.findSourcesByRunId(runId));
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
