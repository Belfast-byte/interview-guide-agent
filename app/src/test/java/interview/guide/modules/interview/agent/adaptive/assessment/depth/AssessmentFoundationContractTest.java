package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentFoundationContractTest {

  private static final Pattern EVIDENCE_TYPE_CHECK_PATTERN = Pattern.compile(
      "evidence_type\\s+IN\\s*\\(([^)]*)\\)"
  );

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
  @DisplayName("评估 schema 拒绝百分制并强制证据来源字段互斥")
  void shouldGuardAssessmentSchema() throws IOException {
    String migration = readMigration("V20260823__add_adaptive_assessment_evidence.sql");

    assertThat(migration)
        .contains("depth_level IN ('L0', 'L1', 'L2', 'L3', 'L4')")
        .contains("evidence_type = 'QUOTE' AND quote_text IS NOT NULL")
        .contains("evidence_type = 'TOOL_RESULT' AND quote_text IS NULL")
        .doesNotContain("overall_score", "percentage_score");
  }

  @Test
  @DisplayName("最新迁移的证据类型 CHECK 约束与 EvidenceType 枚举保持一致，防止约束漂移")
  void shouldAlignLatestEvidenceTypeCheckWithEvidenceTypeEnum() throws IOException {
    String migration = readMigration("V20260909__extend_evidence_type_check_with_code_fact.sql");

    Set<String> expectedTypes = Arrays.stream(EvidenceType.values())
        .map(EvidenceType::name)
        .collect(Collectors.toSet());
    assertThat(extractEvidenceTypeCheckValues(migration)).isEqualTo(expectedTypes);
  }

  private static String readMigration(String fileName) throws IOException {
    try (InputStream input = AssessmentFoundationContractTest.class.getResourceAsStream(
        "/db/migration/" + fileName
    )) {
      assertThat(input).as("迁移文件 %s 必须存在于 classpath", fileName).isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Set<String> extractEvidenceTypeCheckValues(String migration) {
    Matcher matcher = EVIDENCE_TYPE_CHECK_PATTERN.matcher(migration);
    assertThat(matcher.find())
        .as("迁移必须包含 evidence_type IN (...) 的 CHECK 约束")
        .isTrue();
    return Arrays.stream(matcher.group(1).split(","))
        .map(String::strip)
        .map(value -> value.replace("'", ""))
        .collect(Collectors.toSet());
  }
}
