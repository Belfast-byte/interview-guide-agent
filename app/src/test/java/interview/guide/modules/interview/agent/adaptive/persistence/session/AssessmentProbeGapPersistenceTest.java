package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
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
    EpisodeFactPersistence.class,
    AssessmentReconciliationService.class
})
class AssessmentProbeGapPersistenceTest {

  private static final String SESSION_ID = "session-gap";

  @Autowired
  private AdaptiveInterviewPersistenceService service;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AssessmentProbeGapRepository gapRepository;

  @Test
  @DisplayName("记录回答时原子保存 gaps 并让下一 turn 引用 Assessment")
  void shouldPersistGapsAndNextTurnProvenance() {
    createInterview();
    ProbeGap first = new ProbeGap("缓存", "未说明失败边界");
    ProbeGap second = new ProbeGap("版本号", "未说明推进规则");

    service.recordDecision(
        SESSION_ID,
        new CandidateAnswer(1, "使用缓存和版本号"),
        RespondAction.ask("缓存失败时怎么办？", "追问缺口"),
        List.of(),
        null,
        List.of(),
        assessment(List.of(first, second)),
        List.of(),
        List.of()
    );

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
