package cn.name.celestrong.signalbrief.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "signal-brief.mail", name = "enabled", havingValue = "true")
public class JavaMailBriefMailSender implements BriefMailSender {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    public JavaMailBriefMailSender(ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        this.javaMailSenderProvider = javaMailSenderProvider;
    }

    @Override
    public boolean isAvailable() {
        return javaMailSenderProvider.getIfAvailable() != null;
    }

    @Override
    public void send(String from, String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender().send(message);
    }

    private JavaMailSender javaMailSender() {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new BriefMailUnavailableException("邮件发送器未配置");
        }
        return javaMailSender;
    }
}
