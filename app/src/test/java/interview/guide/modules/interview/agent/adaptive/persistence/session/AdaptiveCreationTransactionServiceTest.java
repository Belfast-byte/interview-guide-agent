package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposureEntity;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    interview.guide.modules.interview.agent.adaptive.persistence.session.RubricSnapshotResolver.class,
    AdaptiveCreationTransactionService.class,
    QuestionExposurePersistence.class
})
class AdaptiveCreationTransactionServiceTest {

  @Autowired
  private AdaptiveCreationTransactionService service;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  @Autowired
  private AdaptiveAgentTurnRepository turnRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("首题、最终 Snapshot 与曝光原子保存且重复发布复用唯一 Turn")
  void shouldPublishInitialTurnOnce() {
    InterviewPlan plan = plan();
    AdaptiveSessionCreation creation = creation();
    AgentDecision decision = decision();
    service.create(creation, plan, decision);
    service.create(creation, plan, decision);
    entityManager.flush();
    entityManager.clear();

    var session = sessionRepository.findById(creation.sessionId()).orElseThrow();
    var turns = turnRepository.findBySessionIdOrderByTurnIndex(creation.sessionId());
    Long exposureCount = entityManager.createQuery(
            "select count(e) from QuestionExposureEntity e where e.sessionId = :sessionId",
            Long.class
        )
        .setParameter("sessionId", creation.sessionId())
        .getSingleResult();
    assertThat(session.status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
    assertThat(turns).singleElement().satisfies(turn -> {
      assertThat(turn.question()).isEqualTo("请说明缓存并发更新的冲突处理。");
      assertThat(turn.workingMemory()).isEqualTo(decision.workingMemory());
    });
    assertThat(exposureCount).isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("创建首题失败时，计划和会话一起回滚")
  void shouldRollbackSessionAndPlanWhenFirstTurnFails() {
    var invalidDecision = new AgentDecision(decision().workingMemory(),
        new AgentDecision.Finish("首题不能直接结束"));

    assertThatThrownBy(() -> service.create(creation(), plan(), invalidDecision))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("首轮 Agent 决策必须为 ASK");

    assertThat(sessionRepository.findById("session-1")).isEmpty();
    assertThat(turnRepository.findBySessionIdOrderByTurnIndex("session-1")).isEmpty();
    assertThat(entityManager.createQuery(
        "select count(p) from AdaptiveAgentPlanEntity p where p.sessionId = :sessionId", Long.class)
        .setParameter("sessionId", "session-1").getSingleResult()).isZero();
  }

  private AdaptiveSessionCreation creation() {
    return new AdaptiveSessionCreation(
        null,
        "session-1",
        "candidate-1",
        "JD",
        "Resume",
        "provider-1",
        null,
        null,
        EVALUATION_SETTINGS
    );
  }

  private InterviewPlan plan() {
    return testPlan("session-1", new PlanProposal(List.of(new DimensionProposal(
        "缓存一致性",
        "并发更新",
        "CACHE_CONCURRENCY",
        2,
        "java-backend"
    ))));
  }

  private AgentDecision decision() {
    WorkingMemory memory = new WorkingMemory(
        null,
        new WorkingMemory.Focus("target-0", null, List.of()),
        new WorkingMemory.Deliberation(List.of(), "验证冲突处理", List.of())
    );
    return new AgentDecision(memory, new AgentDecision.Ask(
        "target-0",
        null,
        new AgentDecision.QuestionDraft(
            "请说明缓存并发更新的冲突处理。",
            "验证候选人的并发边界理解",
            List.of()
        )
    ));
  }
}
