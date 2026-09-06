package interview.guide.modules.interview.agent.adaptive.rubric;

import interview.guide.modules.knowledgebase.model.*;
import interview.guide.modules.knowledgebase.repository.*;
import interview.guide.modules.interview.agent.adaptive.persistence.session.RubricSnapshotResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RubricGenerationStore {
  private final RubricGenerationRepository jobs;
  private final KnowledgeBaseRepository knowledgeBases;
  private final KnowledgeBaseQuestionRepository questions;
  private final ObjectMapper mapper;
  public RubricGenerationStore(RubricGenerationRepository jobs, KnowledgeBaseRepository knowledgeBases,
      KnowledgeBaseQuestionRepository questions, ObjectMapper mapper) {
    this.jobs=jobs; this.knowledgeBases=knowledgeBases; this.questions=questions; this.mapper=mapper;
  }
  @Transactional
  public void enqueue(String sessionId, int turnIndex, String dimension, String focus, String question) {
    var job = new RubricGenerationJob(sessionId, turnIndex, dimension, focus, question);
    if (!jobs.existsById(job.id)) jobs.save(job);
  }
  @Transactional(readOnly=true)
  public List<String> pending() {
    return jobs.pending(LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 4));
  }
  @Transactional
  public Claim claim(String id) {
    var j=jobs.locked(id).orElseThrow();
    if (!(j.status.equals("PENDING") || j.status.equals("PROCESSING"))
        || j.availableAt.isAfter(LocalDateTime.now())) return null;
    if (j.attempts >= 3) { j.status="FAILED"; return null; }
    j.attempts++; j.status="PROCESSING"; j.leaseToken=UUID.randomUUID().toString();
    j.updatedAt=LocalDateTime.now(); j.availableAt=j.updatedAt.plusMinutes(10);
    return new Claim(j.id,j.leaseToken,j.dimension,j.focus,j.question,
        j.draftJson == null ? null : mapper.readValue(j.draftJson, RubricGenerationModels.Draft.class));
  }
  @Transactional
  public void saveDraft(Claim claim, RubricGenerationModels.Draft draft, String provider) {
    var j=owned(claim); if (j == null) throw new IllegalStateException("rubric 生成租约已失效");
    if (!draft.valid()) throw new IllegalArgumentException("invalid draft");
    ensureQuestion(j,draft,draft.rubric().replace("\r\n","\n").replace('\r','\n').strip());
    j.draftJson=mapper.writeValueAsString(draft); j.generatorProvider=provider;
    j.updatedAt=LocalDateTime.now();
  }
  @Transactional
  public void complete(Claim claim, RubricGenerationModels.Draft draft,
      RubricGenerationModels.Review review, String generator, String judge) {
    var j=owned(claim);
    if (j == null) return;
    if (draft == null || !draft.valid() || review == null) throw new IllegalArgumentException("rubric 或审核结果不完整");
    String body=draft.rubric().replace("\r\n","\n").replace('\r','\n').strip();
    var q=ensureQuestion(j,draft,body);
    if (!q.getScoringRubric().equals(body)) throw new IllegalStateException("草稿正文已修改，需重新审核");
    q.setStatus(review.passes() ? KnowledgeBaseQuestionStatus.ACTIVE : KnowledgeBaseQuestionStatus.DRAFT);
    j.draftJson=mapper.writeValueAsString(draft); j.reviewJson=mapper.writeValueAsString(review);
    if (j.generatorProvider == null) j.generatorProvider=generator;
    j.judgeProvider=judge;
    j.status=review.passes() ? "APPROVED" : "REVIEW_REQUIRED";
    j.updatedAt=LocalDateTime.now(); j.lastError=null;
  }
  private KnowledgeBaseQuestionEntity ensureQuestion(RubricGenerationJob j,
      RubricGenerationModels.Draft draft, String body) {
    if (j.questionId != null) return questions.findById(j.questionId).orElseThrow();
    var kb=knowledgeBases.findByFileHash(RubricSnapshotResolver.version("agent-generated-rubrics-v1"))
        .orElseGet(() -> {
          var k=new KnowledgeBaseEntity(); k.setName("Agent 自动生成 rubric");
          k.setCategory("评分标准"); k.setOriginalFilename("自动生成条目（无上传文件）");
          k.setFileHash(RubricSnapshotResolver.version("agent-generated-rubrics-v1"));
          k.setVectorStatus(null);
          return knowledgeBases.saveAndFlush(k);
        });
    var q=new KnowledgeBaseQuestionEntity(); q.setKnowledgeBase(kb);
    q.setCategory("自动生成 rubric"); q.setType("SCENARIO"); q.setDifficulty("MEDIUM");
    q.setQuestion(draft.question()); q.setTopicSummary(draft.topic()); q.setScoringRubric(body);
    q.setKeyPointsJson(mapper.writeValueAsString(draft.keyPoints())); q.setFollowUpsJson("[]");
    q.setKbContentHash(kb.getFileHash());
    q.setStatus(KnowledgeBaseQuestionStatus.DRAFT);
    q.setSourceContext(mapper.writeValueAsString(java.util.Map.of(
        "generationJobId",j.id,"reviewPolicy","rubric-judge-v1","synthetic",true)));
    questions.saveAndFlush(q);
    j.questionId=q.getId(); j.rubricVersion=RubricSnapshotResolver.version(body);
    return q;
  }
  @Transactional
  public void failed(Claim claim, String error) {
    var j=owned(claim); if(j==null) return;
    j.lastError=error; j.updatedAt=LocalDateTime.now();
    j.status=j.attempts>=3 ? "FAILED" : "PENDING";
    j.availableAt=j.updatedAt.plusMinutes(j.attempts * 2L);
  }
  private RubricGenerationJob owned(Claim claim) {
    var j=jobs.locked(claim.id()).orElseThrow();
    return j.status.equals("PROCESSING") && claim.token().equals(j.leaseToken)
        && j.availableAt.isAfter(LocalDateTime.now()) ? j : null;
  }
  @Transactional(readOnly=true)
  public Audit audit(String id) {
    var j=jobs.findById(id).orElseThrow();
    return new Audit(j.id,j.status,j.questionId,j.rubricVersion,j.draftJson,j.reviewJson,j.manualReviewsJson,
        j.generatorProvider,j.judgeProvider,j.attempts,j.lastError,j.updatedAt);
  }
  @Transactional(readOnly=true)
  public List<Audit> list(int page) {
    return jobs.findAll(org.springframework.data.domain.PageRequest.of(page, 20,
        org.springframework.data.domain.Sort.by("createdAt").descending())).stream()
        .map(j -> audit(j.id)).toList();
  }
  @Transactional
  public Audit manualReview(String id, boolean approved, String reason, String expectedVersion, String actor) {
    var j=jobs.locked(id).orElseThrow();
    if (j.questionId == null) throw new IllegalStateException("尚无可审核题目");
    var q=questions.findById(j.questionId).orElseThrow();
    String body=q.getScoringRubric().replace("\r\n","\n").replace('\r','\n').strip();
    String version=RubricSnapshotResolver.version(body);
    if (!version.equals(expectedVersion)) throw new IllegalArgumentException("rubric 版本已变化，请重新审核");
    var reviews=j.manualReviewsJson == null ? mapper.createArrayNode()
        : (tools.jackson.databind.node.ArrayNode) mapper.readTree(j.manualReviewsJson);
    reviews.add(mapper.valueToTree(java.util.Map.of("actor",actor,"approved",approved,
        "reason",reason,"rubricVersion",version,"rubric",body,"at",LocalDateTime.now().toString())));
    j.manualReviewsJson=mapper.writeValueAsString(reviews);
    q.setStatus(approved ? KnowledgeBaseQuestionStatus.ACTIVE : KnowledgeBaseQuestionStatus.DRAFT);
    j.rubricVersion=version; j.status=approved ? "MANUALLY_APPROVED" : "REVIEW_REQUIRED";
    j.updatedAt=LocalDateTime.now();
    return audit(id);
  }
  public record Claim(String id,String token,String dimension,String focus,String question,RubricGenerationModels.Draft draft) {}
  public record Audit(String id,String status,Long questionId,String rubricVersion,String draftJson,
      String reviewJson,String manualReviewsJson,String generatorProvider,String judgeProvider,int attempts,String lastError,
      LocalDateTime updatedAt) {}
}
