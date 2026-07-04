package cn.name.celestrong.signalbrief.article;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ArticleQueryService {

    private final ArticleQueryMapper articleQueryMapper;

    public ArticleQueryService(ArticleQueryMapper articleQueryMapper) {
        this.articleQueryMapper = articleQueryMapper;
    }

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
