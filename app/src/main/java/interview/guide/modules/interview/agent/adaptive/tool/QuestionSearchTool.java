package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;

@Component
@RequiredArgsConstructor
public class QuestionSearchTool implements ReadOnlyAgentTool {
  private static final Set<String> ARGUMENTS = Set.of("query", "difficulty");
  private static final int CANDIDATE_MULTIPLIER = 3;
  private final VectorStore vectorStore;
  private final KnowledgeBaseQuestionRepository questions;
  private final ToolProperties properties;

  @Tool(name = "question_search", description = "检索 ACTIVE 题库题目，返回可采用的 question 来源。用于换场景出题。")
  public DecisionObservation query(
      @ToolParam(description = "非空检索内容") String query,
      @ToolParam(description = "可省略的难度过滤，提供时不能为空", required = false) String difficulty,
      ToolContext toolContext) {
    var scope = InterviewToolContext.from(toolContext);
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("query", query);
    if (difficulty != null) arguments.put("difficulty", difficulty);
    var request = new ReadToolRequest(scope.context(), arguments, scope.deadlineNanos());
    validate(request);
    return scope.observe("question_search", execute(request));
  }

  public String name() { return "question_search"; }

  public void validate(ReadToolRequest request) {
    if (!ARGUMENTS.containsAll(request.arguments().keySet())) {
      throw new ReadToolValidationException("arguments", "只接受 query 和 difficulty");
    }
    requireText(request.arguments().get("query"), "query");
    if (request.arguments().containsKey("difficulty")) {
      requireText(request.arguments().get("difficulty"), "difficulty");
    }
  }

  public ReadToolResult execute(ReadToolRequest request) {
    var documents = vectorStore.similaritySearch(SearchRequest.builder()
        .query((String) request.arguments().get("query"))
        .topK(properties.getQuestionBankLimit() * CANDIDATE_MULTIPLIER)
        .similarityThreshold(properties.getQuestionBankMinScore())
        .filterExpression("document_type == '" + QuestionBankVectorIndexer.DOCUMENT_TYPE + "'")
        .build());
    var ids = documents.stream().map(document -> Long.parseLong(
        (String) document.getMetadata().get("question_id"))).distinct().toList();
    var available = questions.findAllById(ids).stream().collect(Collectors.toMap(
        KnowledgeBaseQuestionEntity::getId, Function.identity()));
    Object difficulty = request.arguments().get("difficulty");
    var hits = ids.stream().map(available::get)
        .filter(question -> question != null && question.getStatus() == KnowledgeBaseQuestionStatus.ACTIVE)
        .filter(question -> difficulty == null || difficulty.equals(question.getDifficulty()))
        .limit(properties.getQuestionBankLimit()).map(this::hit).toList();
    if (hits.isEmpty()) return new ReadToolResult.Empty("没有可用的 ACTIVE 题库命中");
    return new ReadToolResult.Success(Map.of("hits", hits), hits.stream().map(hit ->
        new AdoptableSource("question:" + hit.questionId(), "question",
            Long.toString(hit.questionId()), null)).toList());
  }

  private Hit hit(KnowledgeBaseQuestionEntity question) {
    return new Hit(question.getId(), question.getQuestion(), question.getTopicSummary(),
        question.getCategory(), question.getDifficulty());
  }

  private void requireText(Object value, String field) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw new ReadToolValidationException("arguments." + field, "必须为非空字符串");
    }
  }

  record Hit(long questionId, String question, String topicSummary, String category, String difficulty) {}
}
