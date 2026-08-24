package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaWorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.JdbcAbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
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
    AdaptiveInterviewPersistenceService.class,
    AbilityProfileSnapshotService.class,
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class,
    JpaWorkingMemoryFactSource.class
})
class AssessmentProbeGapPersistenceTest {

  private static final String SESSION_ID = "session-gap";

  @Autowired
  private AdaptiveInterviewPersistenceService service;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AssessmentProbeGapRepository gapRepository;

  @Autowired
  private WorkingMemoryFactSource workingMemoryFactSource;

  @Test
  @DisplayName("记录回答时保存 gaps 后让下一 turn 精确引用选中的 gap")
  void shouldPersistGapsAndNextTurnProvenance() {
    createInterview();
    ProbeGap first = new ProbeGap("缓存", "未说明失败边界");
    ProbeGap second = new ProbeGap("版本号", "未说明推进规则");

    service.recordDecision(new AdaptiveDecisionPersistenceInput(
        new MemoryOwner(null, "candidate-1"),
        SESSION_ID,
        new CandidateAnswer(1, "使用缓存和版本号"),
        RespondAction.ask("缓存失败时怎么办？", "追问缺口"),
        List.of(),
        null,
        List.of(),
        assessment(List.of(first, second)),
        List.of(),
        List.of(),
        NextTurnProvenanceDraft.currentAssessmentGap(1, 1)
    ));

    AdaptiveAgentAssessmentEntity assessment = assessmentRepository
        .findBySessionIdAndTurnIndex(SESSION_ID, 1)
        .orElseThrow();
    List<AssessmentProbeGapEntity> gaps = gapRepository
        .findByAssessmentIdOrderByGapOrderAscIdAsc(assessment.id());
    var nextTurn = service.get(SESSION_ID).history().turns().get(1);

    assertThat(gaps).extracting(AssessmentProbeGapEntity::toDomain)
        .containsExactly(first, second);
    assertThat(nextTurn.provenance().trigger().type())
        .isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(nextTurn.provenance().trigger().sourceAssessmentId())
        .isEqualTo(assessment.id());
    assertThat(nextTurn.provenance().trigger().sourceProbeGapId())
        .isEqualTo(gaps.getFirst().id());
    assertThat(workingMemoryFactSource.findProbeGaps(
        new MemoryOwner(null, "candidate-1"),
        SESSION_ID
    )).satisfiesExactly(
        candidate -> assertThat(candidate.gap()).isEqualTo(first),
        candidate -> assertThat(candidate.gap()).isEqualTo(second)
    );
    assertThatThrownBy(() -> workingMemoryFactSource.findProbeGaps(
        new MemoryOwner(null, "candidate-2"),
        SESSION_ID
    )).hasMessageContaining("不存在");
  }

  private void createInterview() {
    service.createSkeleton(new AdaptiveSessionCreation(
        null,
        SESSION_ID,
        "candidate-1",
        "JD",
        "Resume",
        null,
        null,
        null
    ));
    service.completeCreation(
        SESSION_ID,
        InterviewPlan.decide(SESSION_ID, new PlanProposal(List.of(
            new DimensionProposal(
                "专业基础",
                "缓存一致性",
                "REDIS",
                2,
                List.of(),
                "java-backend"
            )
        ))),
        RespondAction.ask("如何保证缓存一致性？", "首题"),
        List.of()
    );
  }

  private AssessmentDecision assessment(List<ProbeGap> gaps) {
    return new AssessmentDecision(
        SESSION_ID,
        1,
        DepthLevel.L2,
        0.8,
        "基础回答",
        false,
        List.of(),
        gaps
    );
  }
}
