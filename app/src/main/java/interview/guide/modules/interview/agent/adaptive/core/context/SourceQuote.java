package interview.guide.modules.interview.agent.adaptive.core.context;

import io.swagger.v3.oas.annotations.media.Schema;
/** 当前答案中的逐字引用；偏移量以 UTF-16 单元计数。 */
public record SourceQuote(Source source, String quote, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer startOffset) {
  public enum Source { ANSWER_TEXT, SUBMITTED_CODE }

  public record Locator(Source source, int startOffset, int endOffset) {}

  public record AnswerSources(String answerText, String submittedCode) {
    public String text(Source source) {
      return switch (source) {
        case ANSWER_TEXT -> answerText;
        case SUBMITTED_CODE -> submittedCode;
      };
    }
  }

  public SourceQuote resolve(AnswerSources sources) {
    if (source == null || quote == null || quote.isBlank()) {
      throw new IllegalArgumentException("引用必须包含来源和非空原句");
    }
    String original = sources.text(source);
    if (original == null) throw new IllegalArgumentException("引用来源没有本轮提交原文");
    int offset = startOffset == null ? uniqueOffset(original) : startOffset;
    if (offset < 0 || offset > original.length() - quote.length()
        || !original.regionMatches(offset, quote, 0, quote.length())) {
      throw new IllegalArgumentException("引用未命中指定来源的原文位置");
    }
    return new SourceQuote(source, quote, offset);
  }

  private int uniqueOffset(String original) {
    int offset = original.indexOf(quote);
    if (offset >= 0 && original.indexOf(quote, offset + 1) >= 0) {
      throw new IllegalArgumentException("引用存在重复片段，请提供 startOffset 消歧");
    }
    return offset;
  }

  public Locator locator() {
    if (startOffset == null) throw new IllegalStateException("引用尚未验证位置");
    return new Locator(source, startOffset, startOffset + quote.length());
  }

  /** 旧文字证据没有位置记录，不为历史数据编造偏移量。 */
  public static SourceQuote fromStored(String quote, Locator locator) {
    return locator == null ? new SourceQuote(Source.ANSWER_TEXT, quote, null)
        : new SourceQuote(locator.source(), quote, locator.startOffset());
  }
}
