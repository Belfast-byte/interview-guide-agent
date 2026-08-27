package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testSession;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
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
@Import({
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class
})
class AbilityCounterPersistenceTest {

  private static final String CANDIDATE_ID = "candidate-1";
  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private EpisodeFactPersistence persistence;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AbilityCounterRepository counterRepository;

  @Test
  @DisplayName("不同 Episode 的 Assessment 按等级增量合并到同一 Counter")
  void shouldIncrementCounterForEachEpisode() {
    persistEpisode("session-1", DepthLevel.L2);
    persistEpisode("session-2", DepthLevel.L4);

    var counter = counterRepository.findCandidateCounter(CANDIDATE_ID, TOPIC)
        .orElseThrow()
        .toDomain();

    assertThat(counter.l2Count()).isEqualTo(1);
    assertThat(counter.l4Count()).isEqualTo(1);
    assertThat(counter.total()).isEqualTo(2);
  }

  private void persistEpisode(String sessionId, DepthLevel level) {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment(sessionId, level))
    );
    persistence.create(session(sessionId), assessment, dimension(sessionId));
  }

  private AdaptiveAgentSessionEntity session(String sessionId) {
    return new AdaptiveAgentSessionEntity(
        testSession(sessionId, 2),
        new AdaptiveSessionCreation(
            null,
            sessionId,
            CANDIDATE_ID,
            "JD",
            "Resume",
            null,
            null,
            null,
            EVALUATION_SETTINGS
        )
    );
  }

  private AssessmentDecision assessment(String sessionId, DepthLevel level) {
    return new AssessmentDecision(
        sessionId,
        1,
        level,
        0.8,
        "已裁决",
        false,
        List.of()
    );
  }

  private PlannedDimension dimension(String sessionId) {
    return testPlan(sessionId, new PlanProposal(List.of(
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
