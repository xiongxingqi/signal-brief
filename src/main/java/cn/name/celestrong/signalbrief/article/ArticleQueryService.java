package cn.name.celestrong.signalbrief.article;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 简报候选文章查询服务。
 *
 * <p>在调用 Mapper 前统一校验时间窗口，避免下游生成简报时出现边界含义不清的查询。</p>
 */
@Service
public class ArticleQueryService {

    private final ArticleQueryMapper articleQueryMapper;

    public ArticleQueryService(ArticleQueryMapper articleQueryMapper) {
        this.articleQueryMapper = articleQueryMapper;
    }

    /**
     * 查找指定半开时间窗口内的简报候选文章。
     */
    public List<Article> findBriefCandidates(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null) {
            throw new IllegalArgumentException("Brief candidate start time must not be null");
        }
        if (endExclusive == null) {
            throw new IllegalArgumentException("Brief candidate end time must not be null");
        }
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Brief candidate start time must be before end time");
        }
        return articleQueryMapper.findBriefCandidates(startInclusive, endExclusive);
    }
}
