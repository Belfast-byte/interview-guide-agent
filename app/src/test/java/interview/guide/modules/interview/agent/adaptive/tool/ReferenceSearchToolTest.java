package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import tools.jackson.databind.ObjectMapper;

class ReferenceSearchToolTest {
  private final SkillReferenceIndex index = mock(SkillReferenceIndex.class);
  private final VectorStore vectors = mock(VectorStore.class);
  private final ToolProperties properties = new ToolProperties();
  private final TokenCountEstimator estimator = mock(TokenCountEstimator.class);
  private final ReferenceSearchTool tool = new ReferenceSearchTool(index, vectors, properties, estimator, new ObjectMapper());

  @Test
  void rejectsForeignTargetsAndCallerSuppliedScope() {
    assertThatThrownBy(() -> tool.validate(request(Map.of("targetId", "foreign", "query", "query"))))
        .isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> tool.validate(request(Map.of("targetId", "target-0", "query", "query", "sessionId", "other"))))
        .isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> tool.validate(request(Map.of("targetId", "target-0", "query", " "))))
        .isInstanceOf(ReadToolValidationException.class);
    verifyNoInteractions(vectors);
  }

  @Test
  void filtersByMappedResourcesDeduplicatesAndProducesNoAdoptableSources() {
    ready();
    var document = new Document("content");
    var chunk = new SkillReferenceIndex.Chunk("id", "source", "title", "content");
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document, document));
    when(index.authoritative(document, Set.of("source"))).thenReturn(chunk);
    var result = (ReadToolResult.Success) tool.execute(request(Map.of("targetId", "target-0", "query", "query")));
    assertThat((List<?>) result.data().get("hits")).hasSize(1);
    assertThat(result.adoptableSources()).isEmpty();
    var search = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectors).similaritySearch(search.capture());
    assertThat(search.getValue().getFilterExpression().toString()).contains("document_type", "skill_reference", "source_id", "source");
  }

  @Test
  void distinguishesUnavailableEmptyStaleAndBudgetOmitted() {
    var request = request(Map.of("targetId", "target-0", "query", "query"));
    assertThat(tool.execute(request)).isInstanceOf(ReadToolResult.Error.class);
    ready();
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    assertThat(tool.execute(request)).isInstanceOf(ReadToolResult.Empty.class);
    var doc = new Document("old");
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
    assertThat(tool.execute(request)).isInstanceOf(ReadToolResult.Error.class);
    when(index.authoritative(doc, Set.of("source"))).thenReturn(new SkillReferenceIndex.Chunk("id", "source", "title", "full text"));
    when(estimator.estimate(anyString())).thenReturn(3000);
    var result = (ReadToolResult.Success) tool.execute(request);
    assertThat((List<?>) result.data().get("hits")).isEmpty();
    assertThat(result.data().get("message").toString()).contains("预算");
  }

  private void ready() {
    when(index.ready()).thenReturn(true);
    when(index.sources(new TopicKey("skill", "focus"))).thenReturn(Set.of("source"));
  }

  private ReadToolRequest request(Map<String, Object> arguments) {
    var target = new CapabilityTarget(new CapabilityTarget.Identity(0, "dimension", "focus", new TopicKey("skill", "focus")),
        new CapabilityTarget.Budget(2, 2), new CapabilityTarget.Depth(DepthLevel.L2, DepthLevel.L4), List.of());
    var context = new AgentContext(new AgentContext.SessionWindow(new AgentContext.SessionIdentity("s", "p", new MemoryOwner(null, "u")),
        SessionMode.EVALUATION, 12), new AgentContext.Facts(new CoverageView(1, 11,
        List.of(new CoverageView.TargetCoverage("target-0", target, 1, null, List.of(), List.of())), List.of(), List.of()),
        List.of(), List.of(), List.of(ReferenceSearchTool.NAME)), WorkingMemory.empty());
    return new ReadToolRequest(context, arguments, Long.MAX_VALUE);
  }
}
