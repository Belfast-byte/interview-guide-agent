package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.tool.RubricVectorIndexRepository.IndexedRubric;
import interview.guide.modules.interview.agent.adaptive.tool.RubricVectorIndexRepository.IndexedSnapshot;
import interview.guide.modules.interview.agent.adaptive.tool.RubricVectorIndexRepository.RubricSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 全局 ACTIVE 审核 rubric 目录的专用语义索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
class RubricVectorIndexer {

  static final String DOCUMENT_TYPE = "adaptive_rubric";

  private final RubricVectorIndexRepository repository;
  private final RubricVectorIndexPersistenceService persistenceService;
  private final VectorStore vectorStore;
  private final ToolProperties properties;

  @Scheduled(
      fixedDelayString = "${app.interview.adaptive-agent.tools.rubric-index-delay:60000}",
      initialDelayString = "${app.interview.adaptive-agent.tools.rubric-index-initial-delay:10000}"
  )
  public void syncNextBatch() {
    removeStaleIndexes();
    indexPendingRubrics();
  }

  private void removeStaleIndexes() {
    List<IndexedRubric> stale = repository.findStale(properties.getRubricIndexBatchSize());
    if (stale.isEmpty()) {
      return;
    }
    vectorStore.delete(stale.stream().map(index -> index.documentId().toString()).toList());
    persistenceService.deleteIndexEntries(
        stale.stream().map(IndexedRubric::questionId).toList());
    log.info("Removed {} stale adaptive rubric vectors", stale.size());
  }

  private void indexPendingRubrics() {
    List<RubricSnapshot> pending = repository.findPending(properties.getRubricIndexBatchSize());
    if (pending.isEmpty()) {
      return;
    }
    List<IndexedSnapshot> indexed = pending.stream().map(rubric -> new IndexedSnapshot(
        rubric.id(), documentId(rubric.id()), rubric.updatedAt())).toList();
    vectorStore.delete(indexed.stream().map(item -> item.documentId().toString()).toList());
    vectorStore.add(pending.stream().map(this::toDocument).toList());
    persistenceService.markIndexed(indexed);
    log.info("Indexed {} active adaptive rubrics", indexed.size());
  }

  private Document toDocument(RubricSnapshot rubric) {
    return Document.builder()
        .id(documentId(rubric.id()).toString())
        .text(indexText(rubric))
        .metadata(Map.of(
            "document_type", DOCUMENT_TYPE,
            "question_id", Long.toString(rubric.id())
        ))
        .build();
  }

  private String indexText(RubricSnapshot rubric) {
    return String.join(
        "\n",
        valueOrEmpty(rubric.category()),
        valueOrEmpty(rubric.topicSummary()),
        rubric.question(),
        rubric.scoringRubric()
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private UUID documentId(long questionId) {
    return UUID.nameUUIDFromBytes(
        ("adaptive-rubric:" + questionId).getBytes(StandardCharsets.UTF_8));
  }
}
