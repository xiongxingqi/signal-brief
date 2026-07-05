package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部 API 的 OpenAPI 文档配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfiguration {

    @Bean
    public OpenAPI signalBriefOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SignalBrief Internal API")
                        .version("v1")
                        .description("SignalBrief 内部手动触发接口文档。"));
    }

    @Bean
    public GroupedOpenApi internalGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/**")
                .packagesToScan("cn.name.celestrong.signalbrief.internal")
                .build();
    }
}
