package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposure;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionSimilarityHit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionSimilaritySearch;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class QuestionExposureVectorStore implements QuestionSimilaritySearch {

  static final String DOCUMENT_TYPE = "candidate_question_exposure";
  private static final int SEMANTIC_CANDIDATES = 20;

  private final VectorStore vectorStore;

  public QuestionExposureVectorStore(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void index(QuestionExposure exposure) {
    vectorStore.add(List.of(Document.builder()
        .id(exposure.embeddingDocumentId())
        .text(exposure.questionText())
        .metadata(metadata(exposure))
        .build()));
  }

  @Override
  public List<QuestionSimilarityHit> search(
      MemoryOwner owner,
      TopicKey topic,
      String question
  ) {
    return vectorStore.similaritySearch(SearchRequest.builder()
            .query(question)
            .topK(SEMANTIC_CANDIDATES)
            .filterExpression(filter(owner, topic))
            .build())
        .stream()
        .map(document -> new QuestionSimilarityHit(
            Long.parseLong(document.getMetadata().get("exposure_id").toString()),
            document.getScore() == null ? 0.0 : document.getScore()
        ))
        .toList();
  }

  private Map<String, Object> metadata(QuestionExposure exposure) {
    return Map.of(
        "document_type", DOCUMENT_TYPE,
        "exposure_id", Long.toString(exposure.exposureId()),
        "tenant_key", tenantKey(exposure.owner()),
        "candidate_id", exposure.owner().candidateId(),
        "skill_id", exposure.identity().topic().skillId(),
        "focus_id", exposure.identity().topic().focusId()
    );
  }

  private String filter(MemoryOwner owner, TopicKey topic) {
    return "document_type == '%s' && tenant_key == '%s' && candidate_id == '%s' "
        .formatted(DOCUMENT_TYPE, tenantKey(owner), owner.candidateId())
        + "&& skill_id == '%s' && focus_id == '%s'"
            .formatted(topic.skillId(), topic.focusId());
  }

  private String tenantKey(MemoryOwner owner) {
    return owner.tenantId() == null ? "candidate" : owner.tenantId();
  }
}
