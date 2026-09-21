package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.TargetCoverage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import tools.jackson.databind.ObjectMapper;

/** 专业背景检索不产生 rubric 采用来源，也不进入正式评分快照。 */
@Component
@RequiredArgsConstructor
public class ReferenceSearchTool implements ReadOnlyAgentTool {
  public static final String NAME = "reference_search";
  private static final String USAGE = "仅为出题背景，不是评分量规或候选人能力证据";
  private final SkillReferenceIndex index;
  private final VectorStore vectors;
  private final ToolProperties properties;
  private final TokenCountEstimator estimator;
  private final ObjectMapper mapper;

  @Tool(name = "reference_search", description = "检索当前目标允许的专业参考。仅供出题背景，不是评分量规或候选人能力证据。")
  public DecisionObservation query(
      @ToolParam(description = "当前计划的目标 ID") String targetId,
      @ToolParam(description = "1 到 1000 字符的非空查询") String query,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("targetId", targetId);
    arguments.put("query", query);
    var request = new ReadToolRequest(scope.context(), arguments, scope.deadlineNanos());
    validate(request);
    return scope.observe("reference_search", execute(request));
  }

  public String name() { return NAME; }

  public void validate(ReadToolRequest request) {
    if (!request.arguments().keySet().equals(Set.of("targetId", "query"))) {
      throw new ReadToolValidationException("arguments", "只接受 targetId 和 query");
    }
    if (!(request.arguments().get("query") instanceof String query) || query.isBlank() || query.length() > 1000) {
      throw new ReadToolValidationException("arguments.query", "必须为 1 到 1000 字符的非空查询");
    }
    target(request);
  }

  public ReadToolResult execute(ReadToolRequest request) {
    var topic = target(request).target().identity().topic();
    if (!index.ready()) return new ReadToolResult.Error("专业参考索引不可用，请勿将其视为没有资料");
    Set<String> sources = index.sources(topic);
    if (sources.isEmpty()) return new ReadToolResult.Empty("该目标未配置专业参考");
    var filter = new FilterExpressionBuilder();
    var documents = vectors.similaritySearch(SearchRequest.builder()
        .query((String) request.arguments().get("query"))
        .topK(properties.getReferenceSearchLimit() * 3)
        .similarityThreshold(properties.getReferenceMinScore())
        .filterExpression(filter.and(filter.eq("document_type", SkillReferenceIndex.DOCUMENT_TYPE),
            filter.in("source_id", sources.toArray())).build()).build());
    var hits = new ArrayList<SkillReferenceIndex.Chunk>();
    var seen = new HashSet<String>();
    int omitted = 0;
    boolean stale = false;
    for (var document : documents) {
      var chunk = index.authoritative(document, sources);
      if (chunk == null) { stale = true; continue; }
      if (!seen.add(chunk.chunkId())) continue;
      hits.add(chunk);
      if (hits.size() > properties.getReferenceSearchLimit()
          || estimator.estimate(mapper.writeValueAsString(Map.of("hits", hits,
              "omitted", documents.size(), "usage", USAGE))) > properties.getReferenceMaxResultTokens()) {
        hits.removeLast();
        omitted++;
      }
    }
    if (hits.isEmpty()) {
      if (stale) return new ReadToolResult.Error("命中参考索引已失效，需要同步索引");
      if (omitted > 0) return new ReadToolResult.Success(
          Map.of("hits", List.of(), "message", "参考片段超过返回预算，未返回正文", "omitted", omitted), List.of());
      return new ReadToolResult.Empty("没有命中该目标的相关专业参考");
    }
    return new ReadToolResult.Success(Map.of("hits", List.copyOf(hits), "omitted", omitted,
        "usage", USAGE), List.of());
  }

  private TargetCoverage target(ReadToolRequest request) {
    Object id = request.arguments().get("targetId");
    return request.context().facts().coverage().targets().stream().filter(t -> t.targetId().equals(id))
        .findFirst().orElseThrow(() -> new ReadToolValidationException("arguments.targetId", "目标不属于当前计划"));
  }
}
