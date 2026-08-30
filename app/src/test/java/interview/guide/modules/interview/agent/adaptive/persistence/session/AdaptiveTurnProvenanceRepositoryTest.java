package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import jakarta.persistence.EntityManager;
import java.util.List;
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
  @DisplayName("问题、provenance 与最终 WorkingMemory 在同一 Turn 保存")
  void shouldPersistTurnBoundaryFacts() {
    repository.saveAndFlush(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
            "session-1",
            2,
            0,
            RespondAction.ask("为什么？", "追问 gap"),
            TurnProvenance.assessmentGap(1, 42, 84)
        ))
    );
    WorkingMemory memory = new WorkingMemory(
        2,
        new WorkingMemory.Focus(
            "target-1",
            84L,
            List.of(new WorkingMemory.GapPriority(84L, "继续核实"))
        ),
        new WorkingMemory.Deliberation(
            List.of(),
            "验证并发边界",
            List.of("question-bank:7")
        )
    );
    AdaptiveAgentTurnEntity saved = repository.saveAndFlush(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
            "session-1",
            3,
            0,
            RespondAction.ask("并发更新时会发生什么？", "验证冲突处理"),
            TurnProvenance.assessmentGap(2, 42, 84),
            memory
        ))
    );
    entityManager.clear();

    AdaptiveAgentTurnEntity reloaded = repository.findById(saved.id()).orElseThrow();

    assertThat(reloaded.question()).isEqualTo("并发更新时会发生什么？");
    assertThat(reloaded.parentTurnIndex()).isEqualTo(2);
    assertThat(reloaded.triggerType()).isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(reloaded.sourceAssessmentId()).isEqualTo(42);
    assertThat(reloaded.sourceProbeGapId()).isEqualTo(84);
    assertThat(reloaded.toDomain().provenance())
        .isEqualTo(TurnProvenance.assessmentGap(2, 42, 84));
    assertThat(reloaded.workingMemory()).isEqualTo(memory);
  }
}
