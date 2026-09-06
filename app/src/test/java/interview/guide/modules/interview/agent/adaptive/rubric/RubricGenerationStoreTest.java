package interview.guide.modules.interview.agent.adaptive.rubric;

import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql=false, properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({RubricGenerationStore.class,RubricGenerationStoreTest.Json.class})
class RubricGenerationStoreTest {
  @TestConfiguration static class Json { @Bean ObjectMapper mapper() { return new ObjectMapper(); } }
  @Autowired RubricGenerationStore store;
  @Autowired RubricGenerationRepository jobs;
  @Autowired KnowledgeBaseQuestionRepository questions;

  @Test void approvalCreatesOneActiveQuestionAndStoresReview() {
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    assertThat(jobs.count()).isEqualTo(1);
    var claim=store.claim(store.pending().getFirst());
    store.saveDraft(claim,draft(),"generator");
    assertThat(store.audit(claim.id()).draftJson()).contains("volatile");
    var review=new RubricGenerationModels.Review(true,true,true,true,true,"事实与锚点通过审核",List.of());
    store.complete(claim,draft(),review,"generator","judge");
    store.complete(claim,draft(),review,"generator","judge");
    assertThat(questions.count()).isEqualTo(1);
    assertThat(questions.findAll().getFirst().getStatus()).isEqualTo(KnowledgeBaseQuestionStatus.ACTIVE);
    assertThat(store.audit(claim.id()).status()).isEqualTo("APPROVED");
    assertThat(store.audit(claim.id()).reviewJson()).contains("事实与锚点通过审核");
    assertThat(store.pending()).isEmpty();
  }

  @Test void claimedApprovalCannotOverrideFailedChecks() {
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    var claim=store.claim(store.pending().getFirst());
    store.complete(claim,draft(),new RubricGenerationModels.Review(true,false,true,true,true,
        "存在事实问题",List.of("volatile 不保证复合操作原子性")),"generator","judge");
    assertThat(questions.findAll().getFirst().getStatus()).isEqualTo(KnowledgeBaseQuestionStatus.DRAFT);
    assertThat(store.audit(claim.id()).status()).isEqualTo("REVIEW_REQUIRED");
  }

  @Test void staleLeaseCannotActivateAndDraftSurvivesRetry() {
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    var first=store.claim(store.pending().getFirst());
    store.saveDraft(first,draft(),"generator");
    var job=jobs.findById(first.id()).orElseThrow();
    job.availableAt=LocalDateTime.now().minusMinutes(1);
    var second=store.claim(first.id());
    assertThat(second.draft()).isEqualTo(draft());
    store.complete(first,draft(),new RubricGenerationModels.Review(true,true,true,true,true,"通过",List.of()),"g","j");
    assertThat(questions.findAll()).singleElement()
        .satisfies(q -> assertThat(q.getStatus()).isEqualTo(KnowledgeBaseQuestionStatus.DRAFT));
    store.failed(second,"Timeout");
    assertThat(store.audit(first.id()).status()).isEqualTo("PENDING");
    assertThat(store.audit(first.id()).draftJson()).isNotBlank();
  }

  @Test void humanReviewChecksVersionAndPreservesJudgeRecord() {
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    var claim=store.claim(store.pending().getFirst());
    store.complete(claim,draft(),new RubricGenerationModels.Review(false,false,true,true,true,
        "交人工确认事实",List.of("需要技术复核")),"generator","judge");
    var audit=store.audit(claim.id());
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        store.manualReview(claim.id(),true,"已复核","wrong-version","admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void humanApprovalRetainsIndependentJudgeResult() {
    store.enqueue("session",1,"Java","volatile","volatile 的原子性边界？");
    var claim=store.claim(store.pending().getFirst());
    store.complete(claim,draft(),new RubricGenerationModels.Review(false,false,true,true,true,
        "交人工确认事实",List.of("需要技术复核")),"generator","judge");
    var reviewed=store.manualReview(claim.id(),true,"已核对规范",store.audit(claim.id()).rubricVersion(),"admin");
    assertThat(reviewed.reviewJson()).contains("需要技术复核");
    assertThat(reviewed.manualReviewsJson()).contains("admin","已核对规范");
    assertThat(questions.findAll().getFirst().getStatus()).isEqualTo(KnowledgeBaseQuestionStatus.ACTIVE);
  }

  private RubricGenerationModels.Draft draft() {
    return new RubricGenerationModels.Draft("volatile 是否使 i++ 原子化？","volatile",
        "L0 无证据；L1 理解可见性；L2 能识别 i++ 的竞态；L3 解释复合操作和内存边界；L4 设计并验证替代方案。",
        List.of("volatile 不保证 i++ 原子性"));
  }
}
