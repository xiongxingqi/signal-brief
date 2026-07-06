package cn.name.celestrong.signalbrief.brief;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BriefGenerationQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final BriefGenerationMapper mapper;

    public BriefGenerationQueryService(BriefGenerationMapper mapper) {
        this.mapper = mapper;
    }

    public List<BriefGeneration> findRecentArchives(Integer limit) {
        return mapper.findRecent(resolveLimit(limit));
    }

    public BriefGeneration findArchive(Long id) {
        return mapper.findById(id)
                .orElseThrow(() -> new BriefGenerationNotFoundException("简报归档记录不存在: " + id));
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
