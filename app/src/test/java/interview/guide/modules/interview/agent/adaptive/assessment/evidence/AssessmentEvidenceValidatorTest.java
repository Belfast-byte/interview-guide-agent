package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.AnswerSources;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.Source;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentEvidenceValidatorTest {
  private final AssessmentEvidenceValidator validator = new AssessmentEvidenceValidator();

  @Test
  void preservesExactCodeAndUtf16Offsets() {
    var sources = new AnswerSources("已修改", "// 😀\nreturn  reserve();");
    var evidence = validator.validate(sources, List.of(candidate(Source.SUBMITTED_CODE, "return  reserve();", null)));
    assertThat(evidence).containsExactly(new ValidatedAssessmentEvidence(EvidenceType.QUOTE,
        "return  reserve();", null, new SourceQuote.Locator(Source.SUBMITTED_CODE, 6, 24)));
  }

  @Test
  void resolvesUniqueQuoteWithoutTrustingAnEstimatedOffset() {
    var sources = new AnswerSources("前文😀。模型调用在事务外执行，提交时核对令牌。", null);
    String quote = "模型调用在事务外执行";
    var evidence = validator.validate(sources, List.of(candidate(Source.ANSWER_TEXT, quote, null)));
    assertThat(evidence.getFirst().quoteLocator())
        .isEqualTo(new SourceQuote.Locator(Source.ANSWER_TEXT, 5, 5 + quote.length()));
    assertThatThrownBy(() -> validator.validate(sources,
        List.of(candidate(Source.ANSWER_TEXT, quote, 4))))
        .hasMessageContaining("suppliedOffset=4", "firstExactMatch=5")
        .hasMessageNotContaining(quote);
  }

  @Test
  void rejectsWhitespaceAndWidthNormalization() {
    var sources = new AnswerSources("使用 Ｒｅｄｉｓ", "return  reserve();");
    assertThatThrownBy(() -> validator.validate(sources,
        List.of(candidate(Source.SUBMITTED_CODE, "return reserve();", null))))
        .isInstanceOf(BusinessException.class).hasMessageContaining("未命中");
    assertThatThrownBy(() -> validator.validate(sources,
        List.of(candidate(Source.ANSWER_TEXT, "使用 Redis", null))))
        .isInstanceOf(BusinessException.class).hasMessageContaining("未命中");
  }

  @Test
  void requiresOffsetForRepeatedFragments() {
    var sources = new AnswerSources(null, "x(); x();");
    assertThatThrownBy(() -> validator.validate(sources,
        List.of(candidate(Source.SUBMITTED_CODE, "x();", null))))
        .isInstanceOf(BusinessException.class).hasMessageContaining("消歧");
    var evidence = validator.validate(sources, List.of(candidate(Source.SUBMITTED_CODE, "x();", 5)));
    assertThat(evidence.getFirst().quoteLocator()).isEqualTo(new SourceQuote.Locator(Source.SUBMITTED_CODE, 5, 9));
  }

  @Test
  void rejectsMissingSourceAndIncorrectOffsets() {
    var sources = new AnswerSources("说明", null);
    assertThatThrownBy(() -> validator.validate(sources,
        List.of(candidate(Source.SUBMITTED_CODE, "说明", null))))
        .isInstanceOf(BusinessException.class).hasMessageContaining("没有本轮提交原文");
    for (int offset : List.of(-1, 1, Integer.MAX_VALUE)) {
      assertThatThrownBy(() -> validator.validate(sources,
          List.of(candidate(Source.ANSWER_TEXT, "说明", offset))))
          .isInstanceOf(BusinessException.class).hasMessageContaining("未命中");
    }
  }

  @Test
  void deduplicatesSameSourcePositionButKeepsOtherSources() {
    var sources = new AnswerSources("CAS", "CAS");
    var text = candidate(Source.ANSWER_TEXT, "CAS", null);
    var code = candidate(Source.SUBMITTED_CODE, "CAS", null);
    assertThat(validator.validate(sources, List.of(text, candidate(Source.ANSWER_TEXT, "CAS", 0), code))).hasSize(2);
  }

  @Test
  void historicalQuoteDoesNotInventPosition() {
    assertThat(SourceQuote.fromStored("历史原句", null).startOffset()).isNull();
  }

  private AssessmentEvidenceCandidate candidate(Source source, String quote, Integer offset) {
    return AssessmentEvidenceCandidate.quote(new SourceQuote(source, quote, offset));
  }
}
