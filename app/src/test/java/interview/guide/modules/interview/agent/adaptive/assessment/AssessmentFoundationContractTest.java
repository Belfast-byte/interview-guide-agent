package interview.guide.modules.interview.agent.adaptive.assessment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentFoundationContractTest {

  @Test
  @DisplayName("回答深度只使用文档定义的 L0 到 L4 五级量规")
  void shouldExposeFiveDepthLevels() {
    assertThat(DepthLevel.values()).containsExactly(
        DepthLevel.L0,
        DepthLevel.L1,
        DepthLevel.L2,
        DepthLevel.L3,
        DepthLevel.L4
    );
    assertThat(DepthLevel.L0.meaning()).isEqualTo("无证据");
    assertThat(DepthLevel.L4.meaning()).isEqualTo("迁移洞察");
  }

  @Test
  @DisplayName("证据只允许原文引用和稳定工具结果")
  void shouldExposeOnlyTraceableEvidenceTypes() {
    assertThat(EvidenceType.values()).containsExactly(
        EvidenceType.QUOTE,
        EvidenceType.TOOL_RESULT,
        EvidenceType.CODE_FACT
    );
  }

  @Test
  @DisplayName("评估 schema 拒绝百分制并强制证据来源二选一")
  void shouldGuardAssessmentSchema() throws IOException {
    String migration;
    try (var input = getClass().getResourceAsStream(
        "/db/migration/V20260823__add_adaptive_assessment_evidence.sql"
    )) {
      migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(migration)
        .contains("depth_level IN ('L0', 'L1', 'L2', 'L3', 'L4')")
        .contains("evidence_type IN ('QUOTE', 'TOOL_RESULT')")
        .contains("evidence_type = 'QUOTE' AND quote_text IS NOT NULL")
        .contains("evidence_type = 'TOOL_RESULT' AND quote_text IS NULL")
        .doesNotContain("overall_score", "percentage_score");
  }
}
