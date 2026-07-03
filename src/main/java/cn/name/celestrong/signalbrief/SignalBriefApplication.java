package cn.name.celestrong.signalbrief;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SignalBriefApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalBriefApplication.class, args);
    }

}
