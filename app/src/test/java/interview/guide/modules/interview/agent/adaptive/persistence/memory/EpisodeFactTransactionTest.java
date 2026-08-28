package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    EpisodeFactPersistence.class,
    SemanticMemoryPersistenceService.class,
    SemanticMemoryRepositories.class,
    SemanticContributionFactory.class,
    SemanticAggregator.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EpisodeFactTransactionTest {

  private static final String SESSION_ID = "episode-rollback-session";

  @Autowired private EpisodeFactPersistence persistence;
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private SemanticContributionRepository contributions;
  @Autowired private SemanticStateRepository states;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("回答事务失败时 Episode 一并回滚")
  void shouldRollbackEpisodeWithAnswerTransaction() {
    EpisodePersistenceInput input = saveSourceFacts();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      persistence.create(input);
      throw new IllegalStateException("answer transaction failed");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(episodes.countBySessionId(SESSION_ID)).isZero();
    assertThat(contributions.count()).isZero();
    assertThat(states.count()).isZero();
  }

  private EpisodePersistenceInput saveSourceFacts() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    return transaction.execute(status -> {
      AdaptiveAgentSessionEntity session = sessions.save(session());
      AdaptiveAgentTurnEntity turn = turns.save(answeredTurn());
      AdaptiveAgentAssessmentEntity assessment = assessments.save(assessment());
      InterviewPlan plan = plan();
      var before = plan.initialWorkState();
      var after = before.withRevision(before.revision() + 1);
      return new EpisodePersistenceInput(
          session, turn, assessment, plan.dimension(0), before, after, null);
    });
  }

  private AdaptiveAgentSessionEntity session() {
    return new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, 2, EVALUATION_SETTINGS),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", EVALUATION_SETTINGS)
    );
  }

  private AdaptiveAgentTurnEntity answeredTurn() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        SESSION_ID,
        1,
        0,
        RespondAction.ask("RDB 和 AOF 如何取舍？", "验证持久化"),
        TurnProvenance.initial()
    ));
    turn.recordAnswer(new CandidateAnswer(1, "RDB 快照，AOF 记录写命令"));
    return turn;
  }

  private AdaptiveAgentAssessmentEntity assessment() {
    return new AdaptiveAgentAssessmentEntity(0, new AssessmentDecision(
        SESSION_ID, 1, DepthLevel.L2, 0.8, "能说明基本差异", List.of()));
  }

  private InterviewPlan plan() {
    return testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend"))));
  }
}
