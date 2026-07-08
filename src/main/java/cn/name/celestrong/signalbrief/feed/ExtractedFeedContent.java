package cn.name.celestrong.signalbrief.feed;

/**
 * 从单个 feed 条目中提取出的文本内容。
 *
 * <p>{@code summaryText} 面向列表展示和 Markdown 草稿，{@code contentText} 面向后续 AI 输入增强。</p>
 */
public record ExtractedFeedContent(String summaryText, String contentText) {
}
