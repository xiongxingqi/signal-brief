package cn.name.celestrong.signalbrief.mail;

/**
 * 邮件发送能力未启用或 SMTP 基础设施不可用时抛出的业务异常。
 */
public class BriefMailUnavailableException extends RuntimeException {

    public BriefMailUnavailableException(String message) {
        super(message);
    }
}
