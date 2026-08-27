package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AssessmentProbeGapRepositoryTest {

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AssessmentProbeGapRepository gapRepository;

  @Test
  @DisplayName("按 gapOrder 和 id 稳定读取同一 Assessment 的缺口")
  void shouldReadGapsInStableOrder() {
    AdaptiveAgentAssessmentEntity assessment = saveAssessment("session-1", 1);
    gapRepository.saveAllAndFlush(List.of(
        gap(assessment, 2, new ProbeGap("版本号", "缺少推进规则")),
        gap(assessment, 1, new ProbeGap("缓存", "缺少失败边界"))
    ));

    assertThat(gapRepository.findByAssessmentIdOrderByGapOrderAscIdAsc(assessment.id()))
        .extracting(AssessmentProbeGapEntity::gapCode)
        .containsExactly("GAP_1", "GAP_2");
  }

  @Test
  @DisplayName("相同 Assessment 下 gapOrder 不允许重复")
  void shouldRejectDuplicateOrderWithinAssessment() {
    AdaptiveAgentAssessmentEntity assessment = saveAssessment("session-1", 1);
    gapRepository.saveAndFlush(gap(
        assessment,
        1,
        new ProbeGap("缓存", "缺少失败边界")
    ));

    assertThatThrownBy(() -> gapRepository.saveAndFlush(
        gap(assessment, 1, new ProbeGap("版本号", "缺少推进规则"))
    )).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("不同 Assessment 可使用相同 gapOrder")
  void shouldIsolateOrderByAssessment() {
    AdaptiveAgentAssessmentEntity first = saveAssessment("session-1", 1);
    AdaptiveAgentAssessmentEntity second = saveAssessment("session-1", 2);

    gapRepository.saveAllAndFlush(List.of(
        gap(first, 1, new ProbeGap("缓存", "缺少失败边界")),
        gap(second, 1, new ProbeGap("并发", "缺少竞态分析"))
    ));

    assertThat(gapRepository.count()).isEqualTo(2);
  }

  private AdaptiveAgentAssessmentEntity saveAssessment(String sessionId, int turnIndex) {
    return assessmentRepository.saveAndFlush(new AdaptiveAgentAssessmentEntity(
        0,
        new AssessmentDecision(
            sessionId,
            turnIndex,
            DepthLevel.L2,
            0.8,
            "达到基础深度",
            List.of()
        )
    ));
  }

  private AssessmentProbeGapEntity gap(
      AdaptiveAgentAssessmentEntity assessment,
      int order,
      ProbeGap gap
  ) {
    return new AssessmentProbeGapEntity(assessment, order, gap);
  }
}
