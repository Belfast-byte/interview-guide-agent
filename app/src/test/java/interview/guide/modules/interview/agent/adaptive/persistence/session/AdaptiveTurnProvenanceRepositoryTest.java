package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdaptiveTurnProvenanceRepositoryTest {

  @Autowired
  private AdaptiveAgentTurnRepository repository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("turn provenance 经数据库往返后保持不变")
  void shouldPersistProvenance() {
    AdaptiveAgentTurnEntity saved = repository.saveAndFlush(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
            "session-1",
            2,
            0,
            RespondAction.ask("为什么？", "追问 gap"),
            TurnProvenance.assessmentGap(1, 42, 84)
        ))
    );
    entityManager.clear();

    AdaptiveAgentTurnEntity reloaded = repository.findById(saved.id()).orElseThrow();

    assertThat(reloaded.parentTurnIndex()).isEqualTo(1);
    assertThat(reloaded.triggerType()).isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(reloaded.sourceAssessmentId()).isEqualTo(42);
    assertThat(reloaded.sourceProbeGapId()).isEqualTo(84);
    assertThat(reloaded.sourceToolResultEventId()).isNull();
    assertThat(reloaded.toDomain().provenance())
        .isEqualTo(TurnProvenance.assessmentGap(1, 42, 84));
  }
}
