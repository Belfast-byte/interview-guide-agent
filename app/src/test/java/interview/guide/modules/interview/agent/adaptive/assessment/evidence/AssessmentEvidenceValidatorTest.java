package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessmentEvidenceValidatorTest {

  private final AssessmentEvidenceValidator validator = new AssessmentEvidenceValidator();

  @Test
  @DisplayName("逐字引用必须是当前回答的精确子串")
  void shouldValidateExactQuote() {
    List<ValidatedAssessmentEvidence> evidences = validator.validate(
        "session-1",
        1,
        "延迟双删只能降低概率，重要数据使用版本号。",
        List.of(AssessmentEvidenceCandidate.quote("重要数据使用版本号"))
    );

    assertThat(evidences).containsExactly(new ValidatedAssessmentEvidence(
        EvidenceType.QUOTE,
        "重要数据使用版本号",
        null
    ));
  }

  @Test
  @DisplayName("模型改写而非逐字引用时明确拒绝正式提案")
  void shouldRejectParaphrasedQuote() {
    assertThatThrownBy(() -> validator.validate(
        "session-1",
        1,
        "重要数据使用版本号。",
        List.of(AssessmentEvidenceCandidate.quote("关键数据应当增加版本字段"))
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("未命中回答原文");
  }

  @Test
  @DisplayName("全半角和连续空白差异归一化后命中回答原文")
  void shouldMatchQuoteAfterNormalization() {
    List<ValidatedAssessmentEvidence> evidences = validator.validate(
        "session-1",
        1,
        "重要数据使用版本号（最终一致），延迟 双删只能降低概率。",
        List.of(
            AssessmentEvidenceCandidate.quote("重要数据使用版本号(最终一致)"),
            AssessmentEvidenceCandidate.quote("延迟 双删只能降低概率")
        )
    );

    assertThat(evidences).containsExactly(
        new ValidatedAssessmentEvidence(
            EvidenceType.QUOTE,
            "重要数据使用版本号(最终一致)",
            null
        ),
        new ValidatedAssessmentEvidence(EvidenceType.QUOTE, "延迟 双删只能降低概率", null)
    );
  }

  @Test
  @DisplayName("模型重复引用同一原文时去重后继续校验而不是整轮失败")
  void shouldDeduplicateRepeatedQuotes() {
    List<ValidatedAssessmentEvidence> evidences = validator.validate(
        "session-1",
        1,
        "延迟双删只能降低概率，重要数据使用版本号。",
        List.of(
            AssessmentEvidenceCandidate.quote("重要数据使用版本号"),
            AssessmentEvidenceCandidate.quote("重要数据使用版本号")
        )
    );

    assertThat(evidences).containsExactly(new ValidatedAssessmentEvidence(
        EvidenceType.QUOTE,
        "重要数据使用版本号",
        null
    ));
  }
}
