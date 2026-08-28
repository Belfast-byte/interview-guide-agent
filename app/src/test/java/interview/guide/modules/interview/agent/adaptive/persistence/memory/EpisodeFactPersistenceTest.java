package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
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

@DataJpaTest(showSql = false, properties = {
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
class EpisodeFactPersistenceTest {

  private static final String SESSION_ID = "episode-write-session";

  @Autowired private EpisodeFactPersistence persistence;
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private SemanticContributionRepository contributions;
  @Autowired private SemanticStateRepository states;

  @Test
  @DisplayName("已回答轮次按真实 turn 和 WorkState revision 生成一条 Episode")
  void shouldPersistEpisodeFromAnsweredTurn() {
    AdaptiveAgentSessionEntity session = saveSession();
    AdaptiveAgentTurnEntity turn = saveAnsweredTurn();
    AdaptiveAgentAssessmentEntity assessment = assessments.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, decision()));
    InterviewPlan plan = plan();
    var before = plan.initialWorkState();
    var after = before.finish(TargetWorkStatus.COMPLETED).withRevision(before.revision() + 1);

    EpisodeFactEntity saved = persistence.create(new EpisodePersistenceInput(
        session, turn, assessment, plan.dimension(0), before, after, null));

    assertThat(episodes.countBySessionId(SESSION_ID)).isEqualTo(1);
    assertThat(contributions.count()).isEqualTo(1);
    assertThat(states.count()).isEqualTo(1);
    assertThat(saved.toDomain()).satisfies(episode -> {
      assertThat(episode.turnId()).isEqualTo(turn.id());
      assertThat(episode.workRevisionBefore()).isEqualTo(before.revision());
      assertThat(episode.workRevisionAfter()).isEqualTo(after.revision());
      assertThat(episode.closureStatus()).isEqualTo(EpisodeClosureStatus.RESOLVED);
    });
  }

  private AdaptiveAgentSessionEntity saveSession() {
    return sessions.saveAndFlush(new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, 2, EVALUATION_SETTINGS),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", EVALUATION_SETTINGS)
    ));
  }

  private AdaptiveAgentTurnEntity saveAnsweredTurn() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        SESSION_ID,
        1,
        0,
        RespondAction.ask("RDB 和 AOF 如何取舍？", "验证持久化"),
        TurnProvenance.initial()
    ));
    turn.recordAnswer(new CandidateAnswer(1, "RDB 快照，AOF 记录写命令"));
    return turns.saveAndFlush(turn);
  }

  private AssessmentDecision decision() {
    return new AssessmentDecision(
        SESSION_ID, 1, DepthLevel.L2, 0.8, "能说明基本差异", List.of());
  }

  private InterviewPlan plan() {
    return testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend"))));
  }
}
