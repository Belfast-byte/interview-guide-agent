package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStateJsonCodec;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    ActionIntentPersistenceService.class,
    ActionIntentJsonCodec.class,
    WorkStatePersistenceService.class,
    WorkStateJsonCodec.class,
    ActionIntentPersistenceServiceTest.JacksonTestConfig.class
})
class ActionIntentPersistenceServiceTest {

  private static final String SESSION_ID = "intent-session";

  @Autowired
  private ActionIntentPersistenceService service;

  @Autowired
  private WorkStatePersistenceService workStateService;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  private InterviewWorkState ready;

  @BeforeEach
  void setUp() {
    InterviewPlan plan = testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend"))));
    InterviewSessionSettings settings = new InterviewSessionSettings(
        SessionMode.EVALUATION, CandidateLevel.CAMPUS, PracticeScope.none());
    sessionRepository.saveAndFlush(new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, plan.maxTurns(), settings),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", settings)
    ));
    InterviewWorkState initial = workStateService.initialize(plan);
    ready = workStateService.apply(patch(
        initial,
        "answer:1",
        List.of(new WorkStateOperation.CompleteAnswer(1))
    ));
  }

  @Test
  @DisplayName("Intent 与 Pending Patch 原子保存并可完成三段事务")
  void shouldPersistThreeTransactions() {
    ActionIntent planned = intent("intent-1", "key-1");
    InterviewWorkState pending = workStateService.get(SESSION_ID);
    service.plan(planned, patch(
        pending,
        "pending:intent-1",
        List.of(new WorkStateOperation.SetPendingAction("intent-1"))
    ));
    ActionIntent executing = service.start("intent-1");
    ActionIntent succeeded = service.succeed(
        "intent-1",
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2")
    );
    InterviewWorkState beforeApply = workStateService.get(SESSION_ID);
    ActionIntent applied = service.apply("intent-1", patch(
        beforeApply,
        "result:intent-1",
        List.of(new WorkStateOperation.ApplyActionResult(
            ActionResultType.QUESTION, 2, "issue-1"))
    ));

    assertThat(executing.progress().status()).isEqualTo(ActionIntentStatus.EXECUTING);
    assertThat(succeeded.progress().status()).isEqualTo(ActionIntentStatus.SUCCEEDED);
    assertThat(applied.progress().status()).isEqualTo(ActionIntentStatus.APPLIED);
    assertThat(workStateService.get(SESSION_ID).activeActionIntentId()).isNull();
  }

  @Test
  @DisplayName("同一会话只允许一个未完成 Intent")
  void shouldRejectSecondActiveIntent() {
    service.plan(intent("intent-1", "key-1"), pendingPatch("intent-1"));

    assertThatThrownBy(() -> service.plan(
        intent("intent-2", "key-2"),
        pendingPatch("intent-2")
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("已有未完成");
  }

  @Test
  @DisplayName("恢复列表只包含 PLANNED SUCCEEDED 和超时 EXECUTING")
  void shouldFindRecoverableIntents() {
    service.plan(intent("intent-1", "key-1"), pendingPatch("intent-1"));

    assertThat(service.recoverable(LocalDateTime.now().plusSeconds(1)))
        .extracting(item -> item.key().intentId())
        .containsExactly("intent-1");
  }

  @Test
  @DisplayName("FAILED Intent 只通过显式 retry 创建新意图并替换 WorkState 引用")
  void shouldRetryFailedIntentExplicitly() {
    service.plan(intent("intent-1", "key-1"), pendingPatch("intent-1"));
    service.start("intent-1");
    service.fail("intent-1", "模型失败");

    ActionIntent retry = service.retry("intent-1");

    assertThat(retry.key().intentId()).isNotEqualTo("intent-1");
    assertThat(retry.payload().idempotencyKey()).isNotEqualTo("key-1");
    assertThat(retry.progress().status()).isEqualTo(ActionIntentStatus.PLANNED);
    assertThat(workStateService.get(SESSION_ID).activeActionIntentId())
        .isEqualTo(retry.key().intentId());
  }

  private WorkStatePatch pendingPatch(String intentId) {
    return patch(
        workStateService.get(SESSION_ID),
        "pending:" + intentId,
        List.of(new WorkStateOperation.SetPendingAction(intentId))
    );
  }

  private ActionIntent intent(String intentId, String key) {
    return ActionIntent.planned(
        new ActionIntentKey(intentId, SESSION_ID, ready.revision()),
        new AskActionPayload(
            new ActionTarget("target-0", "issue-1", 2),
            key,
            new AskActionContext(NextTurnProvenanceDraft.planned(), null)
        ),
        LocalDateTime.now()
    );
  }

  private WorkStatePatch patch(
      InterviewWorkState state,
      String sourceId,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        SESSION_ID + ":" + sourceId,
        SESSION_ID,
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.ACTION_RESULT,
        sourceId,
        operations
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
