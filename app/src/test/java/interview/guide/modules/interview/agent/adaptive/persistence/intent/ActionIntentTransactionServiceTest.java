package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkPhase;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAskIntentCompletion;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptivePlannedAction;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStateJsonCodec;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    ActionIntentTransactionService.class,
    ActionIntentPersistenceService.class,
    ActionIntentJsonCodec.class,
    WorkStatePersistenceService.class,
    WorkStateJsonCodec.class,
    ActionIntentTransactionServiceTest.JacksonTestConfig.class
})
class ActionIntentTransactionServiceTest {

  private static final String SESSION_ID = "creation-intent-session";

  @Autowired private ActionIntentTransactionService transactions;
  @Autowired private ActionIntentPersistenceService intents;
  @Autowired private WorkStatePersistenceService workStates;
  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;

  @Test
  @DisplayName("首题必须先落 ASK Intent，再落轮次并应用结果")
  void shouldCreateFirstTurnThroughIntent() {
    saveSkeleton();
    InterviewPlan plan = plan();
    transactions.initializePlan(SESSION_ID, plan);
    var ready = workStates.get(SESSION_ID);
    AdaptivePlannedAction action = plannedAction(ready.revision());

    transactions.planAction(SESSION_ID, List.of(), action);

    assertThat(intents.get("intent-1").progress().status())
        .isEqualTo(ActionIntentStatus.PLANNED);
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).isEmpty();

    intents.start("intent-1");
    transactions.completeAsk(new AdaptiveAskIntentCompletion(
        SESSION_ID, "intent-1", RespondAction.ask("RDB 和 AOF 如何取舍？", "验证持久化")));

    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).hasSize(1);
    assertThat(intents.get("intent-1").progress().status())
        .isEqualTo(ActionIntentStatus.SUCCEEDED);
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status())
        .isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);

    var pending = workStates.get(SESSION_ID);
    intents.apply("intent-1", resultPatch(pending.revision()));

    assertThat(workStates.get(SESSION_ID).phase()).isEqualTo(WorkPhase.AWAITING_ANSWER);
    assertThat(workStates.get(SESSION_ID).awaitingAnswerTurnIndex()).isEqualTo(1);
  }

  private void saveSkeleton() {
    sessions.save(new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(
            SESSION_ID, AdaptiveInterviewSession.MAX_TURNS, EVALUATION_SETTINGS),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", EVALUATION_SETTINGS)
    ));
  }

  private InterviewPlan plan() {
    return testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend"))));
  }

  private AdaptivePlannedAction plannedAction(long revision) {
    ActionIntent intent = ActionIntent.planned(
        new ActionIntentKey("intent-1", SESSION_ID, revision),
        new AskActionPayload(
            new ActionTarget("target-0", null, 1),
            "intent-1",
            new AskActionContext(NextTurnProvenanceDraft.planned(), null)
        ),
        LocalDateTime.of(2026, 8, 28, 8, 0)
    );
    return new AdaptivePlannedAction(intent, new WorkStatePatch(
        "pending-1", SESSION_ID, revision, revision + 1,
        WorkStatePatchSource.POLICY, "intent:intent-1:pending",
        List.of(new WorkStateOperation.SetPendingAction("intent-1"))
    ));
  }

  private WorkStatePatch resultPatch(long revision) {
    return new WorkStatePatch(
        "result-1", SESSION_ID, revision, revision + 1,
        WorkStatePatchSource.ACTION_RESULT, "intent:intent-1",
        List.of(new WorkStateOperation.ApplyActionResult(
            ActionResultType.QUESTION, 1, null))
    );
  }

  @TestConfiguration
  static class JacksonTestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
