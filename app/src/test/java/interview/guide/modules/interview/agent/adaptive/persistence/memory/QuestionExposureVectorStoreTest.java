package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposure;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class QuestionExposureVectorStoreTest {

  private final VectorStore vectorStore = mock(VectorStore.class);
  private final QuestionExposureVectorStore store =
      new QuestionExposureVectorStore(vectorStore);

  @Test
  @DisplayName("曝光索引使用独立 document type 和 owner topic 元数据")
  void shouldIndexExposureMetadata() {
    store.index(exposure());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(documents.capture());
    Document indexed = documents.getValue().getFirst();
    assertThat(indexed.getId()).isEqualTo("question-exposure:7");
    assertThat(indexed.getMetadata())
        .containsEntry("document_type", "candidate_question_exposure")
        .containsEntry("exposure_id", "7")
        .containsEntry("candidate_id", "candidate-1")
        .containsEntry("focus_id", "REDIS_PERSISTENCE");
  }

  @Test
  @DisplayName("向量召回限定相同 owner 和 TopicKey")
  void shouldSearchWithinOwnerAndTopic() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
        Document.builder()
            .text("历史问题")
            .metadata(Map.of("exposure_id", "7"))
            .build()
    ));

    assertThat(store.search(
        new MemoryOwner(null, "candidate-1"),
        new TopicKey("java-backend", "REDIS_PERSISTENCE"),
        "AOF 重写期间如何保证数据完整？"
    )).extracting("exposureId").containsExactly(7L);

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertThat(request.getValue().getFilterExpression().toString())
        .contains("candidate_question_exposure")
        .contains("candidate-1")
        .contains("REDIS_PERSISTENCE");
  }

  private QuestionExposure exposure() {
    return new QuestionExposure(
        7,
        new MemoryOwner(null, "candidate-1"),
        "session-1",
        11,
        new QuestionIdentity(
            new TopicKey("java-backend", "REDIS_PERSISTENCE"),
            "验证数据丢失边界",
            DepthLevel.L2,
            "L2",
            "scenario",
            "wording"
        ),
        "AOF 重写期间如何保证数据完整？",
        null,
        null,
        "question-exposure:7",
        LocalDateTime.of(2026, 8, 28, 9, 0)
    );
  }
}
