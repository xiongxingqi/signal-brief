package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import cn.name.celestrong.signalbrief.brief.AiBriefGenerationService;
import cn.name.celestrong.signalbrief.brief.BriefArchiveService;
import cn.name.celestrong.signalbrief.brief.BriefGeneration;
import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.config.BriefMailProperties;
import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionOperations;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunQueryService;
import cn.name.celestrong.signalbrief.mail.BriefMailDelivery;
import cn.name.celestrong.signalbrief.mail.BriefMailDeliveryMapper;
import cn.name.celestrong.signalbrief.mail.BriefMailDeliveryService;
import cn.name.celestrong.signalbrief.mail.BriefMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualTriggerControllerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ManualTriggerController.class, TestConfiguration.class);

    @Test
    void doesNotRegisterControllerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertFalse(context.containsBean("manualTriggerController")));
    }

    @Test
    void doesNotRegisterControllerWhenDisabled() {
        contextRunner
                .withPropertyValues("signal-brief.internal-api.enabled=false")
                .run(context -> assertFalse(context.containsBean("manualTriggerController")));
    }

    @Test
    void registersControllerWhenEnabled() {
        contextRunner
                .withPropertyValues("signal-brief.internal-api.enabled=true")
                .run(context -> assertTrue(context.containsBean("manualTriggerController")));
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        FeedIngestionOperations feedIngestionOperations() {
            return triggerType -> FeedIngestionResult.empty();
        }

        @Bean
        BriefGenerationService briefGenerationService() {
            return new BriefGenerationService(null, null);
        }

        @Bean
        AiBriefGenerationService aiBriefGenerationService(BriefGenerationService briefGenerationService) {
            return new AiBriefGenerationService(
                    briefGenerationService,
                    aiSummaryService()
            );
        }

        @Bean
        BriefArchiveService briefArchiveService(BriefGenerationService briefGenerationService) {
            return new BriefArchiveService(briefGenerationService, aiSummaryService(), failingBriefGenerationMapper());
        }

        @Bean
        BriefMailDeliveryService briefMailDeliveryService() {
            return new BriefMailDeliveryService(
                    new BriefMailProperties(false, null, List.of(), null),
                    new FailingBriefMailSenderProvider(),
                    failingBriefGenerationMapper(),
                    failingBriefMailDeliveryMapper()
            );
        }

        @Bean
        RssIngestionRunQueryService rssIngestionRunQueryService() {
            return new RssIngestionRunQueryService(null);
        }

        private AiSummaryService aiSummaryService() {
            return new AiSummaryService(
                    new AiSummaryProperties(false, null, null, null, null, null, null, null),
                    (systemPrompt, userContent) -> {
                        throw new AssertionError("条件测试不会生成 AI 摘要");
                    }
            );
        }

        private BriefGenerationMapper failingBriefGenerationMapper() {
            return new BriefGenerationMapper() {
                @Override
                public Long insertGenerating(Instant startInclusive, Instant endExclusive, String draftMarkdown) {
                    throw new AssertionError("条件测试不会写入简报归档");
                }

                @Override
                public int markSuccess(Long id, String summaryMarkdown, Instant completedAt) {
                    throw new AssertionError("条件测试不会更新简报归档");
                }

                @Override
                public int markFailed(Long id, String errorSummary, Instant completedAt) {
                    throw new AssertionError("条件测试不会更新简报归档");
                }

                @Override
                public Optional<BriefGeneration> findById(Long id) {
                    throw new AssertionError("条件测试不会查询简报归档");
                }
            };
        }

        private BriefMailDeliveryMapper failingBriefMailDeliveryMapper() {
            return new BriefMailDeliveryMapper() {
                @Override
                public Long insertPending(Long briefGenerationId, String recipient, String subject) {
                    throw new AssertionError("条件测试不会写入邮件记录");
                }

                @Override
                public int markSent(Long id, Instant sentAt) {
                    throw new AssertionError("条件测试不会更新邮件记录");
                }

                @Override
                public int markFailed(Long id, String errorSummary) {
                    throw new AssertionError("条件测试不会更新邮件记录");
                }

                @Override
                public Optional<BriefMailDelivery> findById(Long id) {
                    throw new AssertionError("条件测试不会查询邮件记录");
                }

                @Override
                public List<BriefMailDelivery> findByBriefGenerationId(Long briefGenerationId) {
                    throw new AssertionError("条件测试不会查询邮件记录");
                }
            };
        }
    }

    private static class FailingBriefMailSenderProvider implements ObjectProvider<BriefMailSender> {

        @Override
        public BriefMailSender getObject() throws BeansException {
            throw new AssertionError("条件测试不会获取邮件发送器");
        }

        @Override
        public BriefMailSender getIfAvailable() throws BeansException {
            throw new AssertionError("条件测试不会获取邮件发送器");
        }
    }
}
