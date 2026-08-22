package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.IndexedQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankVectorIndexRepository.QuestionSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankVectorIndexerTest {

  @Mock
  private QuestionBankVectorIndexRepository repository;

  @Mock
  private QuestionBankVectorIndexPersistenceService persistenceService;

  @Mock
  private VectorStore vectorStore;

  @Captor
  private ArgumentCaptor<List<Document>> documents;

  private ToolProperties properties;
  private QuestionBankVectorIndexer indexer;

  @BeforeEach
  void setUp() {
    properties = new ToolProperties();
    properties.setQuestionIndexBatchSize(10);
    indexer = new QuestionBankVectorIndexer(
        repository,
        persistenceService,
        vectorStore,
        properties
    );
  }

  @Test
  @DisplayName("只有向量写入成功后才标记题目已索引")
  void shouldMarkQuestionOnlyAfterVectorWriteSucceeds() {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 12, 10, 0);
    when(repository.findStale(10)).thenReturn(List.of());
    when(repository.findPending(10)).thenReturn(List.of(new QuestionSnapshot(
        7L,
        "如何治理 Redis 缓存雪崩？",
        "Redis",
        "缓存稳定性",
        "MEDIUM",
        updatedAt
    )));

    indexer.syncNextBatch();

    String expectedId = documentId(7L).toString();
    verify(vectorStore).delete(List.of(expectedId));
    verify(vectorStore).add(documents.capture());
    assertThat(documents.getValue()).singleElement().satisfies(document -> {
      assertThat(document.getId()).isEqualTo(expectedId);
      assertThat(document.getText())
          .contains("Redis")
          .contains("缓存稳定性")
          .contains("缓存雪崩");
      assertThat(document.getMetadata())
          .containsEntry("document_type", QuestionBankVectorIndexer.DOCUMENT_TYPE)
          .containsEntry("question_id", "7");
    });
    verify(persistenceService).markIndexed(anyList());
  }

  @Test
  @DisplayName("向量写入失败时不会伪造已索引状态")
  void shouldLeaveQuestionPendingWhenVectorWriteFails() {
    when(repository.findStale(10)).thenReturn(List.of());
    when(repository.findPending(10)).thenReturn(List.of(new QuestionSnapshot(
        7L,
        "question",
        "category",
        "topic",
        "MEDIUM",
        LocalDateTime.of(2026, 8, 12, 10, 0)
    )));
    doThrow(new IllegalStateException("embedding unavailable"))
        .when(vectorStore).add(anyList());

    assertThatThrownBy(indexer::syncNextBatch)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("embedding unavailable");

    verify(persistenceService, never()).markIndexed(anyList());
  }

  @Test
  @DisplayName("题目删除或停用后先删向量再删索引状态")
  void shouldRemoveStaleVectorBeforeIndexState() {
    UUID documentId = documentId(9L);
    when(repository.findStale(10)).thenReturn(List.of(new IndexedQuestion(9L, documentId)));
    when(repository.findPending(10)).thenReturn(List.of());

    indexer.syncNextBatch();

    verify(vectorStore).delete(List.of(documentId.toString()));
    verify(persistenceService).deleteIndexEntries(List.of(9L));
  }

  private UUID documentId(long questionId) {
    return UUID.nameUUIDFromBytes(
        ("adaptive-question:" + questionId).getBytes(StandardCharsets.UTF_8)
    );
  }
}
