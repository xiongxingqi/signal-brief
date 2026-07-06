package cn.name.celestrong.signalbrief.mail;

public interface BriefMailSender {

    default boolean isAvailable() {
        return true;
    }

    void send(String from, String recipient, String subject, String text);
}
