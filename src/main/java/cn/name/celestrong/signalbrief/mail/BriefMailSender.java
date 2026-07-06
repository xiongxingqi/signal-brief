package cn.name.celestrong.signalbrief.mail;

/**
 * 简报邮件发送器端口。
 *
 * <p>实现类负责对接具体邮件基础设施，应用服务只依赖这个窄接口。</p>
 */
public interface BriefMailSender {

    /**
     * 返回当前发送器是否具备实际发送条件，例如底层 SMTP Bean 是否存在。
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 发送纯文本简报邮件。
     */
    void send(String from, String recipient, String subject, String text);
}
