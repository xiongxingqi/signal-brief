package cn.name.celestrong.signalbrief.ai;

/**
 * AI 摘要 Provider 适配器。
 *
 * <p>应用层只关心提示词和用户内容，具体协议、认证方式和响应结构由实现类封装。</p>
 */
public interface AiSummaryClient {

    /**
     * 调用外部 Provider 生成摘要正文。
     */
    String summarize(String systemPrompt, String userContent);
}
