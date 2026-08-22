package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.IndexedQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.IndexedSnapshot;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.QuestionSnapshot;
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

/**
 * 题库向量索引构建器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
class QuestionBankVectorIndexer {

  static final String DOCUMENT_TYPE = "interview_question";

  private final QuestionBankVectorIndexRepository repository;
  private final QuestionBankVectorIndexPersistenceService persistenceService;
  private final VectorStore vectorStore;
  private final ToolProperties properties;

  @Scheduled(
      fixedDelayString = "${app.interview.adaptive-agent.tools.question-index-delay:60000}",
      initialDelayString = "${app.interview.adaptive-agent.tools.question-index-initial-delay:10000}"
  )
  public void syncNextBatch() {
    removeStaleIndexes();
    indexPendingQuestions();
  }

  private void removeStaleIndexes() {
    List<IndexedQuestion> stale = repository.findStale(properties.getQuestionIndexBatchSize());
    if (stale.isEmpty()) {
      return;
    }
    vectorStore.delete(stale.stream()
        .map(index -> index.documentId().toString())
        .toList());
    persistenceService.deleteIndexEntries(stale.stream()
        .map(IndexedQuestion::questionId)
        .toList());
    log.info("Removed {} stale adaptive question vectors", stale.size());
  }

  private void indexPendingQuestions() {
    List<QuestionSnapshot> pending = repository.findPending(
        properties.getQuestionIndexBatchSize()
    );
    if (pending.isEmpty()) {
      return;
    }
    List<IndexedSnapshot> indexed = pending.stream()
        .map(question -> new IndexedSnapshot(
            question.id(),
            documentId(question.id()),
            question.updatedAt()
        ))
        .toList();
    vectorStore.delete(indexed.stream()
        .map(snapshot -> snapshot.documentId().toString())
        .toList());
    vectorStore.add(pending.stream().map(this::toDocument).toList());
    persistenceService.markIndexed(indexed);
    log.info("Indexed {} active adaptive question bank entries", indexed.size());
  }

  private Document toDocument(QuestionSnapshot question) {
    return Document.builder()
        .id(documentId(question.id()).toString())
        .text(indexText(question))
        .metadata(Map.of(
            "document_type", DOCUMENT_TYPE,
            "question_id", Long.toString(question.id())
        ))
        .build();
  }

  private String indexText(QuestionSnapshot question) {
    return String.join(
        "\n",
        valueOrEmpty(question.category()),
        valueOrEmpty(question.topicSummary()),
        question.question()
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private UUID documentId(long questionId) {
    return UUID.nameUUIDFromBytes(
        ("adaptive-question:" + questionId).getBytes(StandardCharsets.UTF_8)
    );
  }
}
