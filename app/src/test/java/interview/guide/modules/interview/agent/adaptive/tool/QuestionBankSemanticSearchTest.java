package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankSemanticSearchTest {

  @Mock
  private VectorStore vectorStore;

  @Mock
  private KnowledgeBaseQuestionRepository questionRepository;

  private ToolProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ToolProperties();
    properties.setQuestionBankLimit(2);
    properties.setQuestionBankMinScore(0.4);
  }

  @Test
  @DisplayName("向量召回后仍以关系库状态为准并保留相似度排序")
  void shouldRecheckActiveStatusAndPreserveVectorRanking() {
    KnowledgeBaseQuestionEntity first = question(11L, KnowledgeBaseQuestionStatus.ACTIVE);
    KnowledgeBaseQuestionEntity second = question(22L, KnowledgeBaseQuestionStatus.ACTIVE);
    KnowledgeBaseQuestionEntity draft = question(33L, KnowledgeBaseQuestionStatus.DRAFT);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
        hit(22L),
        hit(33L),
        hit(11L)
    ));
    when(questionRepository.findAllById(List.of(22L, 33L, 11L)))
        .thenReturn(List.of(first, second, draft));
    QuestionBankSemanticSearch search = new QuestionBankSemanticSearch(
        vectorStore,
        questionRepository,
        properties
    );
    QuestionBankSearchTool tool = new QuestionBankSearchTool(search);

    ToolResult result = tool.execute(Map.of("query", "Redis"));

    assertThat(result.resultId()).isEqualTo("question-search:22,11");
    assertThat(result.value().toString())
        .contains("question:22")
        .contains("question:11")
        .doesNotContain("question:33");
    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertThat(request.getValue().getQuery()).isEqualTo("Redis");
    assertThat(request.getValue().getTopK()).isEqualTo(6);
    assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.4);
    assertThat(request.getValue().getFilterExpression().toString())
        .contains(QuestionBankVectorIndexer.DOCUMENT_TYPE);
  }

  private Document hit(long questionId) {
    return Document.builder()
        .text("question")
        .metadata(Map.of("question_id", Long.toString(questionId)))
        .build();
  }

  private KnowledgeBaseQuestionEntity question(
      long id,
      KnowledgeBaseQuestionStatus status
  ) {
    KnowledgeBaseQuestionEntity entity = new KnowledgeBaseQuestionEntity();
    ReflectionTestUtils.setField(entity, "id", id);
    entity.setCategory("Redis");
    entity.setDifficulty("MEDIUM");
    entity.setQuestion("Redis 面试题 " + id);
    entity.setStatus(status);
    return entity;
  }
}
