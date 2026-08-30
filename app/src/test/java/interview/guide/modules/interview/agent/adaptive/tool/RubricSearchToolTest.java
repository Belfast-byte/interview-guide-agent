package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

class RubricSearchToolTest {

  @Test
  @DisplayName("查询参数透传到语义检索并只返回数据库中的 ACTIVE rubric")
  void shouldSearchAndResolveAuthoritativeRubric() {
    VectorStore vectorStore = mock(VectorStore.class);
    KnowledgeBaseQuestionRepository repository = mock(KnowledgeBaseQuestionRepository.class);
    ToolProperties properties = properties();
    RubricSearchTool tool = new RubricSearchTool(vectorStore, repository, properties);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document(7L)));
    when(repository.findAllById(List.of(7L))).thenReturn(List.of(question(7L)));

    ReadToolResult result = tool.execute(new ReadToolRequest(
        context(),
        Map.of("query", "并发更新", "intent", "校准追问", "levelHints", List.of("L3")),
        System.nanoTime() + 1_000_000_000L
    ));

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertThat(request.getValue().getQuery()).isEqualTo("并发更新\n校准追问\nL3");
    assertThat(result).isInstanceOfSatisfying(ReadToolResult.Success.class, success -> {
      assertThat(success.adoptableSources()).singleElement().extracting(AdoptableSource::id)
          .isEqualTo("question:7:rubric");
      assertThat(success.data()).containsKey("hits");
    });
  }

  private ToolProperties properties() {
    ToolProperties properties = new ToolProperties();
    properties.setRubricSearchLimit(2);
    properties.setRubricMinScore(0.4);
    return properties;
  }

  private Document document(long questionId) {
    return Document.builder()
        .id("rubric-" + questionId)
        .text("并发评分标准")
        .metadata(Map.of("question_id", Long.toString(questionId)))
        .build();
  }

  private KnowledgeBaseQuestionEntity question(long id) {
    KnowledgeBaseQuestionEntity question = new KnowledgeBaseQuestionEntity();
    ReflectionTestUtils.setField(question, "id", id);
    question.setStatus(KnowledgeBaseQuestionStatus.ACTIVE);
    question.setCategory("并发");
    question.setDifficulty("HARD");
    question.setScoringRubric("按冲突边界评分");
    return question;
  }

  private AgentContext context() {
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                "session-1", "provider-1", new MemoryOwner("tenant-1", "candidate-1")),
            SessionMode.EVALUATION,
            3
        ),
        new AgentContext.Facts(
            new CoverageView(0, 3, List.of(), List.of(), List.of()),
            List.of(),
            List.of(),
            List.of(RubricSearchTool.NAME)
        ),
        WorkingMemory.empty()
    );
  }
}
