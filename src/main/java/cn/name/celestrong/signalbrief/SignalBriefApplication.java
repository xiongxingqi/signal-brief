package cn.name.celestrong.signalbrief;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口。
 *
 * <p>该类保留在顶层包，确保 Spring 组件扫描覆盖所有业务子包。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SignalBriefApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalBriefApplication.class, args);
    }

}
