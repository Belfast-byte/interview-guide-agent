package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AssessmentRevision;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EpisodeFactPersistence.class, AssessmentReconciliationService.class})
class AssessmentReconciliationServiceTest {

  private static final String SESSION_ID = "session-revision";
  private static final String CANDIDATE_ID = "candidate-1";
  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private EpisodeFactPersistence episodePersistence;

  @Autowired
  private AssessmentReconciliationService reconciliationService;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AbilityCounterRepository counterRepository;

  @Test
  @DisplayName("等级修订原子执行旧等级递减和新等级递增")
  void shouldCompensateLevelCounts() {
    persistEpisode();

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L4));

    var counter = counter().toDomain();
    assertThat(counter.l2Count()).isZero();
    assertThat(counter.l4Count()).isEqualTo(1);
  }

  @Test
  @DisplayName("等级未变化时计数保持不变")
  void shouldNotChangeCounterForSameLevel() {
    persistEpisode();

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L2));

    assertThat(counter().toDomain().l2Count()).isEqualTo(1);
  }

  @Test
  @DisplayName("旧等级计数不足时明确暴露下溢错误")
  void shouldRejectCounterUnderflow() {
    persistEpisode();
    AbilityCounterEntity counter = counter();
    counter.decrement(DepthLevel.L2);
    counterRepository.saveAndFlush(counter);

    assertThatThrownBy(() -> reconciliationService.reconcile(
        revision(DepthLevel.L2, DepthLevel.L4)
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("下溢");
  }

  private void persistEpisode() {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment())
    );
    episodePersistence.create(session(), assessment, dimension());
  }

  private AbilityCounterEntity counter() {
    return counterRepository.findCandidateCounter(CANDIDATE_ID, TOPIC).orElseThrow();
  }

  private AssessmentRevision revision(DepthLevel oldLevel, DepthLevel newLevel) {
    return new AssessmentRevision(SESSION_ID, 1, oldLevel, newLevel);
  }

  private AssessmentDecision assessment() {
    return new AssessmentDecision(
        SESSION_ID,
        1,
        DepthLevel.L2,
        0.8,
        "初始评估",
        false,
        List.of()
    );
  }

  private AdaptiveAgentSessionEntity session() {
    return new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, 2),
        new AdaptiveSessionCreation(
            null,
            SESSION_ID,
            CANDIDATE_ID,
            "JD",
            "Resume",
            null,
            null,
            null
        )
    );
  }

  private PlannedDimension dimension() {
    return InterviewPlan.decide(SESSION_ID, new PlanProposal(List.of(
        new DimensionProposal(
            "专业基础",
            "缓存一致性",
            TOPIC.focusId(),
            2,
            List.of(),
            TOPIC.skillId()
        )
    ))).dimensions().getFirst();
  }
}
