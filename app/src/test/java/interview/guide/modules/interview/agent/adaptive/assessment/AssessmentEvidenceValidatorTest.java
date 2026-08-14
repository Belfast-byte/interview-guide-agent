package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentEvidenceValidatorTest {

  @Test
  @DisplayName("逐字引用必须是当前回答的精确子串")
  void shouldValidateExactQuote() {
    AssessmentEvidenceValidator validator = quoteValidator();

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
  @DisplayName("模型改写而非逐字引用时快速失败")
  void shouldRejectParaphrasedQuote() {
    assertThatThrownBy(() -> quoteValidator().validate(
        "session-1",
        1,
        "重要数据使用版本号。",
        List.of(AssessmentEvidenceCandidate.quote("关键数据应当增加版本字段"))
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法追溯");
  }

  @Test
  @DisplayName("工具证据按同一会话轮次批量解析稳定结果 ID")
  void shouldResolveToolResultInOneBatch() {
    AtomicReference<Set<String>> requestedIds = new AtomicReference<>();
    AssessmentEvidenceValidator validator = new AssessmentEvidenceValidator(
        (sessionId, turnIndex, resultIds) -> {
          requestedIds.set(resultIds);
          return new AssessmentEvidenceFacts(Map.of("sandbox:run-1", 42L));
        }
    );

    List<ValidatedAssessmentEvidence> evidences = validator.validate(
        "session-1",
        1,
        "回答",
        List.of(AssessmentEvidenceCandidate.toolResult("sandbox:run-1"))
    );

    assertThat(requestedIds.get()).containsExactly("sandbox:run-1");
    assertThat(evidences).containsExactly(new ValidatedAssessmentEvidence(
        EvidenceType.TOOL_RESULT,
        null,
        42L
    ));
  }

  private AssessmentEvidenceValidator quoteValidator() {
    return new AssessmentEvidenceValidator((sessionId, turnIndex, resultIds) -> {
      throw new AssertionError("纯文本引用不应查询工具结果");
    });
  }
}
