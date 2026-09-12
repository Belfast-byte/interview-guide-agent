package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.*;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.*;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(AdaptiveAssessmentRepositories.class)
class GapResolutionPersistenceTest {
  @Autowired AdaptiveAssessmentRepositories store;
  @Autowired AssessmentProbeGapRepository gaps;
  @Autowired jakarta.persistence.EntityManager em;
  @Test void closurePersistsOnlySpecifiedGapWithCurrentAssessment() {
    var first=store.saveAssessment(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision("session",1,DepthLevel.L1,0.8,"概念复述",List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS", null)))));
    var saved=store.saveGaps(List.of(
        new AssessmentProbeGapEntity(first,1,new ProbeGap(new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, "CAS", 5),"未解释重试")),
        new AssessmentProbeGapEntity(first,2,new ProbeGap(new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, "CAS", 5),"未解释 ABA"))));
    long resolvedId=saved.getFirst().id();
    var second=store.saveAssessment(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision("session",2,DepthLevel.L2,0.8,"补足重试机制",List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS 重试", null)))));
    store.resolveGaps("session",0,second,List.of(new GapResolution(resolvedId,new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, "CAS 重试", 15),"解释了失败重试")));
    em.flush(); em.clear();
    var restored = gaps.findById(resolvedId).orElseThrow();
    assertThat(restored.closedByAssessmentId()).isEqualTo(second.id());
    assertThat(restored.assessmentTurnIndex()).isEqualTo(1);
    assertThat(restored.anchorLocator()).isEqualTo(new SourceQuote.Locator(SourceQuote.Source.SUBMITTED_CODE, 5, 8));
    assertThat(restored.closureEvidenceLocator()).isEqualTo(new SourceQuote.Locator(SourceQuote.Source.SUBMITTED_CODE, 15, 21));
    assertThat(gaps.findOpenForTarget("session",0)).singleElement()
        .satisfies(g -> assertThat(g.toDomain().missingPoint()).isEqualTo("未解释 ABA"));
    assertThat(em.createNativeQuery("select closure_evidence_quote from agent_assessment_probe_gaps where id = :id",String.class)
        .setParameter("id",resolvedId).getSingleResult()).isEqualTo("CAS 重试");
  }
  @Test void reviewAndEvidenceRetainTheirOwnAssessmentAndSource() {
    var review = new CodeRepairReview(List.of(new CodeRepairReview.CheckReview(
        "C1", CodeRepairReview.Result.UNDETERMINED, "缺少事务隔离条件")));
    var assessment = store.saveAssessment(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision("review-session", 2, DepthLevel.L2, 0.8, "静态审阅",
            List.of(), List.of(), List.of(), review)));
    var locator = new SourceQuote.Locator(SourceQuote.Source.SUBMITTED_CODE, 5, 8);
    var evidence = store.saveEvidences(List.of(new AdaptiveAgentEvidenceEntity(assessment, "review-session", 2,
        new interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence(
            interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType.QUOTE,
            "CAS", null, locator)))).getFirst();
    em.flush(); em.clear();
    assertThat(store.assessment("review-session", 2).orElseThrow().codeReview()).isEqualTo(review);
    var restored = em.find(AdaptiveAgentEvidenceEntity.class, evidence.id());
    assertThat(restored.assessment().id()).isEqualTo(assessment.id());
    assertThat(restored.sourceTurnIndex()).isEqualTo(2);
    assertThat(restored.quoteLocator()).isEqualTo(locator);
    assertThat(restored.sandboxExecutionId()).isNull();
  }

  @Test void historicalGapKeepsUnknownLocation() {
    var assessment = store.saveAssessment(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision("history", 1, DepthLevel.L1, 0.8, "旧评估", List.of())));
    var saved = store.saveGaps(List.of(new AssessmentProbeGapEntity(assessment, 1,
        new ProbeGap(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "历史", 0), "解释缺失")))).getFirst();
    em.createNativeQuery("update agent_assessment_probe_gaps set anchor_locator_json = null where id = :id")
        .setParameter("id", saved.id()).executeUpdate();
    em.clear();
    var restored = gaps.findById(saved.id()).orElseThrow();
    assertThat(restored.anchorLocator()).isNull();
    assertThat(restored.toDomain().anchor().startOffset()).isNull();
    assertThat(restored.toDomain().anchor().quote()).isEqualTo("历史");
  }

}
