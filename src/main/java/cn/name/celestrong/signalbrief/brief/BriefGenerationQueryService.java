package cn.name.celestrong.signalbrief.brief;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简报归档查询服务。
 *
 * <p>集中处理分页上限和不存在记录的业务异常，HTTP 层只负责协议转换。</p>
 */
@Service
public class BriefGenerationQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final BriefGenerationMapper mapper;

    public BriefGenerationQueryService(BriefGenerationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询最近归档记录，限制最大返回数量，避免内部接口误传大 limit 拖慢数据库。
     */
    public List<BriefGeneration> findRecentArchives(Integer limit) {
        return mapper.findRecent(resolveLimit(limit));
    }

    /**
     * 按 ID 查询归档；不存在时抛出业务异常供内部接口统一映射为 404。
     */
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
