package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentBackfillTurn;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaAssessmentBackfillStore.class)
class JpaAssessmentBackfillStoreTest {

  @Autowired
  private JpaAssessmentBackfillStore store;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  @Autowired
  private AdaptiveAgentPlanRepository planRepository;

  @Autowired
  private AdaptiveAgentTurnRepository turnRepository;

  @Test
  @DisplayName("回填存储只读取缺少评估的完整历史轮次且可重复执行")
  void shouldLoadAndPersistOnlyMissingAssessment() {
    String sessionId = "session-backfill";
    InterviewPlan plan = InterviewPlan.decide(
        sessionId,
        new PlanProposal(List.of(new DimensionProposal(
            "架构设计",
            "缓存权衡",
            "ARCHITECTURE",
            1,
            List.of(),
            "java-backend"
        )))
    );
    sessionRepository.save(new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(sessionId, 1).start(),
        null,
        "candidate-old",
        "JD",
        "Resume",
        "provider-a"
    ));
    planRepository.save(new AdaptiveAgentPlanEntity(
        sessionId,
        plan.dimensions().getFirst()
    ));
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(
        sessionId,
        1,
        0,
        RespondAction.ask("完整历史问题？", "验证权衡")
    );
    turn.complete(
        new CandidateAnswer(1, "完整历史回答包含成本与一致性"),
        RespondAction.finish("完成", "历史裁决")
    );
    turnRepository.save(turn);

    List<AssessmentBackfillTurn> missing = store.findMissing(sessionId);

    assertThat(missing).singleElement().satisfies(fact -> {
      assertThat(fact.question()).isEqualTo("完整历史问题？");
      assertThat(fact.answer()).isEqualTo("完整历史回答包含成本与一致性");
      assertThat(fact.dimension()).isEqualTo("架构设计");
      assertThat(fact.focus()).isEqualTo("缓存权衡");
    });
    store.save(
        missing.getFirst(),
        new AssessmentDecision(
            sessionId,
            1,
            DepthLevel.L3,
            0.9,
            "展示了权衡",
            true,
            List.of("成本与一致性")
        ),
        List.of(new ValidatedAssessmentEvidence(
            EvidenceType.QUOTE,
            "成本与一致性",
            null
        ))
    );

    assertThat(store.findMissing(sessionId)).isEmpty();
  }
}
