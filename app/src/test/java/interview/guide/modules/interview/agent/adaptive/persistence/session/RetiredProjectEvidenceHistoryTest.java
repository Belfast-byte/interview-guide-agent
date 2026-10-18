package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerDecisionService.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.core.action.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetiredProjectEvidenceHistoryTest extends AdaptiveAnswerPersistenceFixture {
  @Test
  void legacyCodeFactStillLoadsAndAppearsInReportAfterProducerRemoval() {
    var plan = initializeInterview();
    var owner = new MemoryOwner(null, "candidate-1");
    var answer = new CandidateAnswer(1, "历史回答");
    claims.claim(SESSION_ID, owner, answer, "historical-finish", Duration.ofMinutes(1));
    var assessmentDecision = new AssessmentDecision(SESSION_ID, 1, DepthLevel.L2, 0.8,
        "历史评估", List.of(), List.of());
    var assessed = new AnswerAssessment(plan.dimension(0), assessmentDecision, List.of());
    var finish = new AgentDecision(WorkingMemory.empty(), new AgentDecision.Finish("本场结束"));
    transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(owner, interview(plan),
        new AdaptiveAnswerTransactionService.CommitFacts(answer,
            new AnswerProgressionDecision(assessed, finish)), "historical-finish"));
    var assessment = assessments.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow();
    // 按历史存储格式写入，验证旧记录无需已删除的仓库分析实体即可恢复。
    entityManager.createNativeQuery("""
        INSERT INTO agent_evidences
          (assessment_id, evidence_type, source_session_id, source_turn_index,
           code_source_id, code_anchor, code_fact_usage, created_at)
        VALUES (:assessmentId, :type, :sessionId, :turnIndex,
                :sourceId, :anchor, :usage, :createdAt)
        """)
        .setParameter("assessmentId", assessment.id())
        .setParameter("type", "CODE_FACT")
        .setParameter("sessionId", SESSION_ID)
        .setParameter("turnIndex", 1)
        .setParameter("sourceId", "historical-digest")
        .setParameter("anchor", "src/OrderService.java:42")
        .setParameter("usage", "CLAIM_VERIFICATION")
        .setParameter("createdAt", LocalDateTime.now())
        .executeUpdate();
    entityManager.clear();

    assertThat(evidences.findReportEvidence(SESSION_ID)).singleElement().satisfies(evidence -> {
      assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.CODE_FACT);
      assertThat(evidence.codeFactUsage()).isEqualTo(CodeFactUsage.CLAIM_VERIFICATION);
    });
    assertThat(reports.candidateReport(SESSION_ID).projectSources()).singleElement().satisfies(source -> {
      assertThat(source.sourceId()).isEqualTo("historical-digest");
      assertThat(source.anchor()).isEqualTo("src/OrderService.java:42");
      assertThat(source.usage()).isEqualTo(CodeFactUsage.CLAIM_VERIFICATION);
    });
  }
}
