package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简报邮件投递记录查询服务。
 *
 * <p>先校验归档是否存在，再返回投递列表，避免空列表同时表示“未发送”和“归档不存在”。</p>
 */
@Service
public class BriefMailDeliveryQueryService {

    private final BriefGenerationMapper briefGenerationMapper;
    private final BriefMailDeliveryMapper deliveryMapper;

    public BriefMailDeliveryQueryService(
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper
    ) {
        this.briefGenerationMapper = briefGenerationMapper;
        this.deliveryMapper = deliveryMapper;
    }

    /**
     * 查询指定简报归档的所有邮件投递记录。
     */
    public List<BriefMailDelivery> findDeliveries(Long briefGenerationId) {
        briefGenerationMapper.findById(briefGenerationId)
                .orElseThrow(() -> new BriefGenerationNotFoundException(
                        "简报归档记录不存在: " + briefGenerationId
                ));
        return deliveryMapper.findByBriefGenerationId(briefGenerationId);
    }
}
