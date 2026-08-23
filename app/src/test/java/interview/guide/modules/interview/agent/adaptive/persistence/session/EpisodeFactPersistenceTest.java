package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityCounterRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
    AdaptiveInterviewPersistenceService.class,
    AbilityProfileSnapshotService.class,
    EpisodeFactPersistence.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class
})
class EpisodeFactPersistenceTest {

  private static final String SESSION_ID = "session-episode";
  private static final String CANDIDATE_ID = "candidate-episode-test";

  @Autowired
  private AdaptiveInterviewPersistenceService service;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private AbilityCounterRepository counterRepository;

  @BeforeEach
  void createInterview() {
    service.createSkeleton(new AdaptiveSessionCreation(
        null,
        SESSION_ID,
        CANDIDATE_ID,
        "JD",
        "Resume",
        null,
        null,
        null
    ));
    service.completeCreation(
        SESSION_ID,
        plan(),
        RespondAction.ask("如何保证缓存一致性？", "首题"),
        List.of()
    );
  }

  @Test
  @DisplayName("完成回答时同步创建一个 PENDING EpisodeFact")
  void shouldCreateEpisodeWithAssessment() {
    recordFirstAnswer();

    EpisodeFactEntity episode = episodeRepository
        .findBySessionIdAndTurnIndex(SESSION_ID, 1)
        .orElseThrow();

    assertThat(episode.toDomain()).satisfies(fact -> {
      assertThat(fact.owner()).isEqualTo(new MemoryOwner(null, CANDIDATE_ID));
      assertThat(fact.topic()).isEqualTo(new TopicKey("java-backend", "REDIS"));
      assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.PENDING);
      assertThat(fact.assessmentId()).isPositive();
    });
  }

  @Test
  @DisplayName("过期回答失败时不创建 EpisodeFact")
  void shouldNotCreateEpisodeForRejectedAnswer() {
    assertThatThrownBy(() -> service.recordDecision(new AdaptiveDecisionPersistenceInput(
        SESSION_ID,
        new CandidateAnswer(2, "过期回答"),
        RespondAction.ask("下一题", "继续"),
        List.of(),
        null,
        List.of(),
        assessment(2),
        List.of(),
        List.of(),
        NextTurnProvenanceDraft.planned()
    ))).isInstanceOf(BusinessException.class);

    assertThat(episodeRepository.countBySessionId(SESSION_ID)).isZero();
  }

  @Test
  @DisplayName("相同回答重放不产生第二个 EpisodeFact")
  void shouldNotDuplicateEpisodeOnReplay() {
    recordFirstAnswer();

    assertThatThrownBy(this::recordFirstAnswer)
        .isInstanceOf(BusinessException.class);
    assertThat(episodeRepository.countBySessionId(SESSION_ID)).isEqualTo(1);
    assertThat(counterRepository.findCandidateCounter(
        CANDIDATE_ID,
        new TopicKey("java-backend", "REDIS")
    ).orElseThrow().toDomain().l2Count()).isEqualTo(1);
  }

  private void recordFirstAnswer() {
    service.recordDecision(new AdaptiveDecisionPersistenceInput(
        SESSION_ID,
        new CandidateAnswer(1, "通过版本号保证一致性"),
        RespondAction.ask("失败时怎么办？", "继续追问"),
        List.of(),
        null,
        List.of(),
        assessment(1),
        List.of(),
        List.of(),
        NextTurnProvenanceDraft.planned()
    ));
  }

  private AssessmentDecision assessment(int turnIndex) {
    return new AssessmentDecision(
        SESSION_ID,
        turnIndex,
        DepthLevel.L2,
        0.8,
        "基础回答",
        false,
        List.of()
    );
  }

  private InterviewPlan plan() {
    return InterviewPlan.decide(SESSION_ID, new PlanProposal(List.of(
        new DimensionProposal(
            "专业基础",
            "缓存一致性",
            "REDIS",
            2,
            List.of(),
            "java-backend"
        )
    )));
  }
}
