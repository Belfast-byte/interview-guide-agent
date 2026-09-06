package interview.guide.modules.interview.agent.adaptive.memory.observation;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.*;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.*;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.*;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static interview.guide.modules.interview.agent.adaptive.memory.observation.MemoryObservationProposal.*;

@DataJpaTest(showSql=false,properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({JpaMemoryEvidenceService.class,EpisodeEnrichmentPersistenceService.class,AdaptiveAgentProperties.class})
class MemoryEvidenceIntegrationTest {
  @Autowired JpaMemoryEvidenceService memory;
  @Autowired MemoryObservationRepository revisions;
  @Autowired EpisodeFactRepository episodes;
  @Autowired AdaptiveAgentAssessmentRepository assessments;
  @Autowired EpisodeEnrichmentPersistenceService store;
  @MockitoBean EpisodeEnrichmentContextSource context;
  static final MemoryOwner OWNER=new MemoryOwner(null,"candidate-memory");
  static final TopicKey TOPIC=new TopicKey("java","atomicity");
  final List<MemoryObservationContext.PriorObservation> prior=new ArrayList<>();

  @Test void volatileErrorAssistedCorrectionIndependentTransferAndRetraction() {
    var first=observe("first-session",1,"volatile 是否让 i++ 原子化？","volatile 让 i++ 原子化",
        proposal(Finding.INCORRECT,Assistance.NONE,null,Relation.NEW,false,false,"volatile 让 i++ 原子化",null));
    assertThat(belief().state()).isEqualTo("NEEDS_REVALIDATION");
    var corrected=observe("first-session",2,"请拆分读改写","读改写之间存在竞态",
        proposal(Finding.CORRECT,Assistance.HINT,first.id,Relation.CORRECTION,true,false,"读改写之间存在竞态","请拆分读改写"));
    assertThat(belief().state()).isEqualTo("CORRECTED_WITH_ASSISTANCE");
    assertThat(belief().independentOpportunities()).isEqualTo(1);
    assertThat(belief().needsVerification()).isTrue();
    var verified=observe("new-session",1,"库存检查后扣减有何问题？","检查与扣减之间存在竞态",
        proposal(Finding.CORRECT,Assistance.NONE,corrected.id,Relation.SUPPORT,true,true,"检查与扣减之间存在竞态",null));
    assertThat(belief().state()).isEqualTo("INDEPENDENTLY_DEMONSTRATED");
    assertThat(belief().needsVerification()).isFalse();
    String ref=JpaMemoryEvidenceService.reference(belief());
    assertThat(memory.adopt(OWNER,TOPIC,List.of(ref))).containsExactly(ref);
    memory.retract(OWNER,verified.id,"当前验证回答被错误解释");
    assertThat(belief().state()).isEqualTo("CORRECTED_WITH_ASSISTANCE");
    assertThat(belief().needsVerification()).isTrue();
    assertThat(memory.audit(OWNER,verified.id)).hasSize(2);
    assertThatThrownBy(() -> memory.adopt(OWNER,TOPIC,List.of(ref))).hasMessageContaining("记忆已变化");
    memory.retract(OWNER,first.id,"原始观察来源无效");
    assertThat(memory.beliefs(OWNER,TOPIC)).isEmpty();
  }

  @Test void differentSessionAloneDoesNotProveTransfer() {
    var first=observe("a",1,"是否原子？","是原子操作",
        proposal(Finding.INCORRECT,Assistance.NONE,null,Relation.NEW,false,false,"是原子操作",null));
    observe("b",1,"是否原子？","不是原子操作",
        proposal(Finding.CORRECT,Assistance.NONE,first.id,Relation.SUPPORT,true,false,"不是原子操作",null));
    assertThat(belief().needsVerification()).isTrue();
    assertThat(belief().state()).isNotEqualTo("INDEPENDENTLY_DEMONSTRATED");
  }

  @Test void sourceScopeAndQuotesAreEnforced() {
    var first=observe("a",1,"是否原子？","不是原子操作",
        proposal(Finding.CORRECT,Assistance.NONE,null,Relation.NEW,false,false,"不是原子操作",null));
    assertThatThrownBy(() -> memory.requireCurrent(new MemoryOwner("tenant","candidate-memory"),first.id))
        .hasMessageContaining("不属于当前用户");
    assertThatThrownBy(() -> memory.requireCurrent(new MemoryOwner(null,"other"),first.id))
        .hasMessageContaining("不属于当前用户");
    assertThatThrownBy(() -> observe("b",1,"说明机制","没有解释",
        proposal(Finding.CORRECT,Assistance.NONE,first.id,Relation.SUPPORT,true,true,"伪造引用",null)))
        .hasMessageContaining("原文");
  }

  @Test void independentContradictionKeepsBothSides() {
    var first=observe("a",1,"是否原子？","不是原子操作",
        proposal(Finding.CORRECT,Assistance.NONE,null,Relation.NEW,false,false,"不是原子操作",null));
    observe("b",1,"库存扣减是否有竞态？","不存在竞态",
        proposal(Finding.INCORRECT,Assistance.NONE,first.id,Relation.CONTRADICTION,true,true,"不存在竞态",null));
    assertThat(belief().state()).isEqualTo("MIXED_EVIDENCE");
    assertThat(belief().evidenceRevisionIds()).hasSize(2);
  }

  @Test void errorAfterSuccessRequiresVerificationEvenWithoutIndependentCredit() {
    var first=observe("a",1,"是否原子？","不是原子操作",
        proposal(Finding.CORRECT,Assistance.NONE,null,Relation.NEW,false,false,"不是原子操作",null));
    observe("a",2,"再解释一次","一定原子",
        proposal(Finding.INCORRECT,Assistance.NONE,first.id,Relation.CONTRADICTION,true,false,"一定原子",null));
    assertThat(belief().state()).isEqualTo("NEEDS_REVALIDATION");
    assertThat(belief().needsVerification()).isTrue();
    assertThat(belief().evidenceRevisionIds()).hasSize(2);
  }

  private CapabilityBelief belief() { return memory.beliefs(OWNER,TOPIC).getFirst(); }
  private MemoryObservationProposal proposal(Finding f,Assistance a,Long related,Relation rel,
      boolean same,boolean different,String quote,String assistanceQuote) {
    return new MemoryObservationProposal(0,f,quote,a,assistanceQuote,related,rel,same,different,false,"基于本次作答与实际提示条件判断");
  }
  private MemoryObservationRevision observe(String session,int turn,String question,String answer,MemoryObservationProposal p) {
    var assessment=assessments.saveAndFlush(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision(session,turn,DepthLevel.L2,0.8,"当前回答评估",List.of(answer))));
    var episode=episodes.saveAndFlush(new EpisodeFactEntity(new EpisodeFactCreation(OWNER,session,
        SessionMode.PRACTICE,assessment.id(),turn,TOPIC,"target-0",EpisodeAssistanceLevel.NONE,
        EpisodeClosureStatus.UNRESOLVED,null),assessment));
    var input=new EpisodeEnrichmentRequest(episode.id(),session,turn,TOPIC,question,answer,DepthLevel.L2,
        "当前评估",List.of(),List.of(),new MemoryObservationContext(List.of("区分可见性与复合操作原子性"),
            "volatile 不保证复合操作原子性",List.of(),List.of(),prior,true,turn>1,prior.stream().map(MemoryObservationContext.PriorObservation::question).toList()));
    when(context.load(episode.id())).thenReturn(input);
    String token=store.claim(episode.id()).orElseThrow().executionToken();
    var completion=new EpisodeEnrichmentCompletion(episode.id(),token,"当前回答观察",List.of(),input,p,"test-model");
    store.complete(completion); store.complete(completion);
    var row=revisions.findByEpisodeIdOrderByRevisionDesc(episode.id()).getFirst();
    assertThat(revisions.findByEpisodeIdOrderByRevisionDesc(episode.id())).hasSize(1);
    prior.add(new MemoryObservationContext.PriorObservation(row.id,session,row.capabilityKey,row.objective,
        question,row.summary,p));
    return row;
  }
}
