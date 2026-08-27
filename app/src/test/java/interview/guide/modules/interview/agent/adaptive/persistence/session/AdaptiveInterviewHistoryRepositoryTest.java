package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testSession;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewHistoryService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("自适应面试历史分页查询测试")
class AdaptiveInterviewHistoryRepositoryTest {

  private static final int HISTORY_COUNT = 21;

  @Autowired
  private AdaptiveAgentSessionRepository repository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("只返回本人非租户会话且按创建时间倒序分页")
  void historyIsCandidateScopedAndTenantFree() {
    List<AdaptiveAgentSessionEntity> sessions = new ArrayList<>();
    for (int index = 0; index < HISTORY_COUNT; index++) {
      sessions.add(session("candidate-a-" + index, "candidate-a", null));
    }
    sessions.add(session("candidate-b", "candidate-b", null));
    sessions.add(session("tenant-session", "candidate-a", "tenant-a"));
    repository.saveAllAndFlush(sessions);
    setCandidateHistoryTimes();
    entityManager.clear();

    var firstPage = repository.findCandidateHistory(
        "candidate-a",
        PageRequest.of(0, AdaptiveInterviewHistoryService.PAGE_SIZE)
    );
    var secondPage = repository.findCandidateHistory(
        "candidate-a",
        PageRequest.of(1, AdaptiveInterviewHistoryService.PAGE_SIZE)
    );

    assertThat(firstPage.getTotalElements()).isEqualTo(HISTORY_COUNT);
    assertThat(firstPage.getContent()).hasSize(20);
    assertThat(firstPage.getContent().getFirst().getSessionId()).isEqualTo("candidate-a-20");
    assertThat(secondPage.getContent()).singleElement()
        .extracting(AdaptiveInterviewSummaryProjection::getSessionId)
        .isEqualTo("candidate-a-0");
  }

  private AdaptiveAgentSessionEntity session(
      String sessionId,
      String candidateId,
      String tenantId
  ) {
    return new AdaptiveAgentSessionEntity(
        testSession(sessionId, 4),
        new AdaptiveSessionCreation(
            tenantId,
            sessionId,
            candidateId,
            "JD " + sessionId,
            "Resume",
            null,
            null,
            null,
            EVALUATION_SETTINGS
        )
    );
  }

  private void setCandidateHistoryTimes() {
    LocalDateTime base = LocalDateTime.of(2026, 8, 22, 10, 0);
    for (int index = 0; index < HISTORY_COUNT; index++) {
      entityManager.createQuery("""
              UPDATE AdaptiveAgentSessionEntity s
              SET s.createdAt = :createdAt
              WHERE s.id = :sessionId
              """)
          .setParameter("createdAt", base.plusMinutes(index))
          .setParameter("sessionId", "candidate-a-" + index)
          .executeUpdate();
    }
  }
}
