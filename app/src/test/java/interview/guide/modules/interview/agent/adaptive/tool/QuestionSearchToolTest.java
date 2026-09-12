package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class QuestionSearchToolTest {
  private final VectorStore vectors = mock(VectorStore.class);
  private final KnowledgeBaseQuestionRepository questions = mock(KnowledgeBaseQuestionRepository.class);
  private final QuestionSearchTool tool = new QuestionSearchTool(vectors, questions, new ToolProperties());

  @Test
  void rechecksActiveDatabaseRowsAndDifficultyPreservingRankAndSources() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc(3), doc(1), doc(2), doc(4)));
    var active = question(1, "hard", KnowledgeBaseQuestionStatus.ACTIVE);
    var easier = question(2, "easy", KnowledgeBaseQuestionStatus.ACTIVE);
    var draft = question(3, "hard", KnowledgeBaseQuestionStatus.DRAFT);
    when(questions.findAllById(List.of(3L, 1L, 2L, 4L))).thenReturn(List.of(active, easier, draft));
    var request = request(Map.of("query", "库存预留", "difficulty", "hard"));
    tool.validate(request);
    var result = (ReadToolResult.Success) tool.execute(request);
    assertThat(result.adoptableSources()).singleElement().satisfies(source ->
        assertThat(source.reference()).isEqualTo("question:1"));
    assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("referenceAnswer", "scoringRubric");
    var capture = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectors).similaritySearch(capture.capture());
    assertThat(capture.getValue().getFilterExpression().toString()).contains("interview_question").doesNotContain("skill");
  }

  @Test
  void noAuthoritativeHitIsEmptyAndVectorFailureStaysFailure() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    assertThat(tool.execute(request(Map.of("query", "query")))).isInstanceOf(ReadToolResult.Empty.class);
    when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("vector down"));
    assertThatThrownBy(() -> tool.execute(request(Map.of("query", "query"))))
        .isInstanceOf(IllegalStateException.class).hasMessage("vector down");
  }

  @Test
  void rejectsUnsupportedFiltersAndBlankDifficulty() {
    assertThatThrownBy(() -> tool.validate(request(Map.of("query", "q", "skillId", "java"))))
        .isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> tool.validate(request(Map.of("query", "q", "difficulty", " "))))
        .isInstanceOf(ReadToolValidationException.class);
  }

  private Document doc(long id) {
    return Document.builder().text("index").metadata(Map.of("question_id", Long.toString(id))).build();
  }

  private KnowledgeBaseQuestionEntity question(long id, String difficulty, KnowledgeBaseQuestionStatus status) {
    var question = mock(KnowledgeBaseQuestionEntity.class);
    when(question.getId()).thenReturn(id);
    when(question.getDifficulty()).thenReturn(difficulty);
    when(question.getStatus()).thenReturn(status);
    when(question.getQuestion()).thenReturn("业务问题");
    return question;
  }

  private ReadToolRequest request(Map<String, Object> arguments) {
    return new ReadToolRequest(null, arguments, Long.MAX_VALUE);
  }
}
