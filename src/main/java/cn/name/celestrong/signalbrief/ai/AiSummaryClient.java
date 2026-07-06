package cn.name.celestrong.signalbrief.ai;

public interface AiSummaryClient {

    String summarize(String systemPrompt, String userContent);
}
