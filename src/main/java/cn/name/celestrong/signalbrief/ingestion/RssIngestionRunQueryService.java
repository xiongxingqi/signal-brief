package cn.name.celestrong.signalbrief.ingestion;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RssIngestionRunQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RssIngestionRunMapper mapper;

    public RssIngestionRunQueryService(RssIngestionRunMapper mapper) {
        this.mapper = mapper;
    }

    public List<RssIngestionRun> findRecentRuns(Integer limit) {
        return mapper.findRecentRuns(resolveLimit(limit));
    }

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
