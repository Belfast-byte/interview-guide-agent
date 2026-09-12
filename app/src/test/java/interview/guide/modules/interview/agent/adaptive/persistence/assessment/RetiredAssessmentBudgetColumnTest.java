package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RetiredAssessmentBudgetColumnTest {

  private static final String SESSION_ID = "retired-budget-session";
  private static final int OLD_TURN = 3;
  private static final int LATEST_TURN = 5;
  private static final int DIMENSION_ORDER = 0;
  private static final double CONFIDENCE = 0.9;

  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("停用预算映射后保留旧列真值，新评估通过数据库默认值正常写入")
  void shouldPreserveHistoricalColumnAndInsertNewAssessments() {
    // 模拟旧迁移留下的真实列；Hibernate 的新实体模型不再生成该列。
    jdbc.execute("ALTER TABLE agent_assessments "
        + "ADD COLUMN budget_exhausted_final BOOLEAN NOT NULL DEFAULT FALSE");
    try {
      verifyHistoricalColumn();
    } finally {
      jdbc.update("DELETE FROM agent_assessments WHERE session_id = ?", SESSION_ID);
      jdbc.execute("ALTER TABLE agent_assessments DROP COLUMN budget_exhausted_final");
    }
  }

  private void verifyHistoricalColumn() {
    var historical = assessments.saveAndFlush(assessment(OLD_TURN, DepthLevel.L1));
    jdbc.update("UPDATE agent_assessments SET budget_exhausted_final = TRUE WHERE id = ?",
        historical.id());
    var latest = assessments.saveAndFlush(assessment(LATEST_TURN, DepthLevel.L3));
    entityManager.clear();

    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(SESSION_ID))
        .extracting(AdaptiveAgentAssessmentEntity::depthLevel)
        .containsExactly(DepthLevel.L1, DepthLevel.L3);
    assertThat(jdbc.queryForObject(
        "SELECT budget_exhausted_final FROM agent_assessments WHERE id = ?",
        Boolean.class, historical.id())).isTrue();
    assertThat(jdbc.queryForObject(
        "SELECT budget_exhausted_final FROM agent_assessments WHERE id = ?",
        Boolean.class, latest.id())).isFalse();
  }

  private AdaptiveAgentAssessmentEntity assessment(int turnIndex, DepthLevel depth) {
    return new AdaptiveAgentAssessmentEntity(DIMENSION_ORDER, new AssessmentDecision(
        SESSION_ID, turnIndex, depth, CONFIDENCE, "本轮正式评估",
        List.of("当前回答原文"), List.of()));
  }
}
