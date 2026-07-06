package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<BriefMailDelivery> findDeliveries(Long briefGenerationId) {
        briefGenerationMapper.findById(briefGenerationId)
                .orElseThrow(() -> new BriefGenerationNotFoundException(
                        "简报归档记录不存在: " + briefGenerationId
                ));
        return deliveryMapper.findByBriefGenerationId(briefGenerationId);
    }
}
