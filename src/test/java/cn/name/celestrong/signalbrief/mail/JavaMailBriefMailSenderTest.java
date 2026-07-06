package cn.name.celestrong.signalbrief.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaMailBriefMailSenderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JavaMailBriefMailSender.class);

    @Test
    void doesNotRegisterSenderWhenPropertyIsMissing() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("javaMailBriefMailSender"));
            assertTrue(context.getBeansOfType(BriefMailSender.class).isEmpty());
        });
    }

    @Test
    void doesNotRegisterSenderWhenDisabled() {
        contextRunner
                .withPropertyValues("signal-brief.mail.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("javaMailBriefMailSender"));
                    assertTrue(context.getBeansOfType(BriefMailSender.class).isEmpty());
                });
    }

    @Test
    void registersUnavailableSenderWhenEnabledWithoutJavaMailSender() {
        contextRunner
                .withPropertyValues("signal-brief.mail.enabled=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.containsBean("javaMailBriefMailSender"));
                    BriefMailSender sender = context.getBean(BriefMailSender.class);
                    assertInstanceOf(JavaMailBriefMailSender.class, sender);
                    assertFalse(sender.isAvailable());
                });
    }

    @Test
    void registersSenderWhenEnabledWithJavaMailSender() {
        contextRunner
                .withPropertyValues("signal-brief.mail.enabled=true")
                .withBean(JavaMailSender.class, TestJavaMailSender::new)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.containsBean("javaMailBriefMailSender"));
                    BriefMailSender sender = context.getBean(BriefMailSender.class);
                    assertInstanceOf(JavaMailBriefMailSender.class, sender);
                    assertTrue(sender.isAvailable());
                });
    }

    @Test
    void registersSenderWhenEnabledWithSpringMailAutoConfiguration() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues(
                        "signal-brief.mail.enabled=true",
                        "spring.mail.host=smtp.example.com"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.containsBean("javaMailBriefMailSender"));
                    BriefMailSender sender = context.getBean(BriefMailSender.class);
                    assertInstanceOf(JavaMailBriefMailSender.class, sender);
                    assertTrue(sender.isAvailable());
                });
    }

    static class TestJavaMailSender implements JavaMailSender {

        @Override
        public MimeMessage createMimeMessage() {
            return null;
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return null;
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
        }
    }
}
