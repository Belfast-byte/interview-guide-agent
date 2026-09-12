package interview.guide.modules.interview.agent.adaptive.persistence.session;

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
        new AssessmentDecision("session",1,DepthLevel.L1,0.8,"概念复述",List.of("CAS"))));
    var saved=store.saveGaps(List.of(
        new AssessmentProbeGapEntity(first,1,new ProbeGap("CAS","未解释重试")),
        new AssessmentProbeGapEntity(first,2,new ProbeGap("CAS","未解释 ABA"))));
    long resolvedId=saved.getFirst().id();
    var second=store.saveAssessment(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision("session",2,DepthLevel.L2,0.8,"补足重试机制",List.of("CAS 重试"))));
    store.resolveGaps("session",0,second,List.of(new GapResolution(resolvedId,"CAS 重试","解释了失败重试")));
    em.flush(); em.clear();
    assertThat(gaps.findById(resolvedId).orElseThrow().closedByAssessmentId()).isEqualTo(second.id());
    assertThat(gaps.findOpenForTarget("session",0)).singleElement()
        .satisfies(g -> assertThat(g.toDomain().missingPoint()).isEqualTo("未解释 ABA"));
    assertThat(em.createNativeQuery("select closure_evidence_quote from agent_assessment_probe_gaps where id = :id",String.class)
        .setParameter("id",resolvedId).getSingleResult()).isEqualTo("CAS 重试");
  }
}
